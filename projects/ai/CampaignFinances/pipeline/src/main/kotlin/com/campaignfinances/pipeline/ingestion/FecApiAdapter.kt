package com.campaignfinances.pipeline.ingestion

import com.campaignfinances.pipeline.db.DbConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.nio.file.Path
import java.sql.Connection
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Source adapter for `api.open.fec.gov`'s schedule_a endpoint
 * (docs/TDS_PHASE1.md §3): incremental top-ups between bulk loads, never a
 * full load — the API's 1,000-calls/hour rate limit makes that impractical
 * (see [FecApiClient]).
 *
 * Fetches every contribution with `contribution_receipt_date` on or after
 * the watermark (the last successful `fec-api` run's `finished_at`, or the
 * start of [cycle] if this is the first `fec-api` run ever), writes it to
 * `staging_contribution` with `source = "fec-api"` provenance, then reuses
 * [CanonicalLoader.loadContributions] **unchanged** to normalize into the
 * canonical schema — the exact same canonical-load code path bulk ingests
 * use (PR-154). Candidates/committees/linkages are not part of this
 * adapter's scope; the API only ever incrementally tops up contributions,
 * so `committee`/`candidate` rows must already exist from a prior bulk load
 * for [CanonicalLoader.loadContributions] to resolve `committee_id`.
 *
 * **Known limitation (PR-155 scoping decision):** an amended/corrected FEC
 * record republished via the API under the same `sub_id` that bulk already
 * loaded creates a *second* canonical row, because canonical
 * `contribution.source` differs (`fec-bulk` vs `fec-api`) and
 * `UNIQUE(source, source_record_id)` is keyed on that pair — it does not
 * dedupe across the two ingestion channels. Unifying the `source` value
 * would fix this but means touching the already-shipped `FecBulkAdapter`
 * provenance constant, which was deliberately left alone here; cross-channel
 * reconciliation is deferred to PR-158's reconciliation report instead.
 *
 * @param dbConfig connection settings (no LOCAL INFILE needed — this
 *   adapter's volumes are incremental, not bulk)
 * @param apiConfig the FEC API key
 * @param httpClient must have `ContentNegotiation` + JSON installed; a
 *   production default is provided, tests inject a `MockEngine`-backed client.
 *   Owned by this adapter either way — closed at the end of [ingest] (PR-183).
 * @param out where progress and the watermark line are printed
 */
class FecApiAdapter(
    private val dbConfig: DbConfig,
    apiConfig: FecApiConfig,
    private val httpClient: HttpClient = defaultHttpClient(),
    private val out: Appendable = System.out,
) : BulkIngestRunner {

    companion object {
        /** Provenance label written to every staging row and ingest_run. */
        const val SOURCE = "fec-api"

        private val WATERMARK_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")

        /** Bulk's staging date format, shared so [CanonicalLoader.loadContributions]'s `STR_TO_DATE` works unchanged regardless of source. */
        private val STAGING_DATE_FORMAT = DateTimeFormatter.ofPattern("MMddyyyy")

        private fun defaultHttpClient(): HttpClient = HttpClient(CIO) {
            install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        }
    }

    private val apiClient = FecApiClient(httpClient, apiConfig.apiKey)

    /**
     * Runs one incremental top-up: fetches since the watermark, stages, then
     * canonicalizes. On any failure, removes this run's own partially-staged
     * rows (PR-180) and records whatever was staged before the failure in
     * `ingest_run.row_counts` (PR-184) rather than discarding it. Always
     * closes [httpClient] before returning, success or failure (PR-183).
     *
     * @param cycle the two-year federal election cycle; used only as the
     *   watermark fallback when no prior successful `fec-api` run exists
     * @param localDir ignored — the API has no local files to read from;
     *   present only because this adapter shares the [BulkIngestRunner]
     *   interface with [FecBulkAdapter]
     * @return the run id and the totals staged
     */
    override fun ingest(cycle: Int, localDir: Path?): IngestSummary = runBlocking {
        try {
            connect().use { connection ->
                val runId = IngestRunRepository.start(connection, SOURCE)
                var progress = FileCounts(0, 0)
                try {
                    val minDate = watermark(connection, cycle)
                    out.appendLine("fetching since $minDate")
                    progress = fetchAndStage(connection, runId, minDate) { progress = it }
                    val canonical = CanonicalLoader(connection).loadContributions(runId)
                    IngestRunRepository.finish(connection, runId, "SUCCESS", mapOf("indiv" to progress, "contribution" to canonical))
                    IngestSummary(runId, mapOf("indiv" to progress))
                } catch (e: Exception) {
                    deleteStagingForRun(connection, runId)
                    IngestRunRepository.finish(connection, runId, "FAILED", mapOf("indiv" to progress))
                    throw e
                }
            }
        } finally {
            httpClient.close()
        }
    }

    /**
     * The last successful `fec-api` run's `finished_at` date, or [cycle]'s
     * first day if this is the first `fec-api` run ever (nothing to top up
     * before the cycle starts). Delegates to [IngestRunRepository] (PR-188)
     * so all `ingest_run` reads/writes go through one place.
     *
     * @param connection an open connection; not closed by this method
     * @param cycle the two-year federal election cycle, used as the fallback
     * @return the watermark date, formatted `yyyy-MM-dd`
     */
    private fun watermark(connection: Connection, cycle: Int): String =
        IngestRunRepository.latestSuccessfulFinishedAt(connection, SOURCE)
            ?: LocalDate.of(cycle - 1, 1, 1).format(WATERMARK_FORMAT)

    /**
     * Pages through every contribution since [minDate], inserting each
     * page's valid rows into `staging_contribution` as it arrives, and
     * reporting the running total to [onProgress] after every page so a
     * failure partway through still leaves an accurate count of what was
     * staged before it (PR-184). Stops once the API reports no further
     * pages.
     *
     * @param minDate the watermark to fetch contributions since
     * @param onProgress called with the cumulative [FileCounts] after each
     *   page is staged
     * @return the final cumulative [FileCounts] once paging completes
     */
    private suspend fun fetchAndStage(
        connection: Connection,
        runId: Long,
        minDate: String,
        onProgress: (FileCounts) -> Unit,
    ): FileCounts {
        var cursor: LastIndexes? = null
        var loaded = 0L
        var bad = 0L
        while (true) {
            val page = apiClient.fetchPage(minDate, cursor)
            // The API's response shape is well-typed JSON (unlike
            // pipe-delimited bulk text), so the only "malformed row" case
            // here is a missing amount — contribution.amount is NOT NULL,
            // and unlike loadCandidates, loadContributions does not filter
            // nulls itself, so a null amount must be rejected before
            // staging or it fails the whole run (PR-179).
            val (good, missingAmount) = page.results.partition { it.contributionReceiptAmount != null }
            insertStaging(connection, runId, good)
            loaded += good.size
            bad += missingAmount.size
            onProgress(FileCounts(loaded, bad))
            if (page.pagination.page >= page.pagination.pages || page.results.isEmpty()) break
            // Fail fast rather than silently restarting from page 1 (PR-187):
            // a null cursor on a non-final page would reset to the first page
            // and loop forever, accumulating duplicate staging rows.
            cursor = page.pagination.lastIndexes
                ?: error("FEC API returned null lastIndexes on page ${page.pagination.page} of ${page.pagination.pages}")
        }
        out.appendLine("[fec-api] loaded $loaded contributions into staging_contribution" + if (bad > 0) ", $bad skipped (missing amount)" else "")
        return FileCounts(loaded, bad)
    }

    /**
     * Batches one page's results into a single multi-row INSERT, clipping
     * free-text fields to `staging_contribution`'s column widths (PR-181).
     *
     * @param connection an open connection; not closed by this method
     * @param runId the `ingest_run` id to attribute these rows to
     * @param results the pre-filtered page results to insert; must not contain
     *   rows with a null `contributionReceiptAmount` (caller's responsibility)
     */
    private fun insertStaging(connection: Connection, runId: Long, results: List<ScheduleAResult>) {
        if (results.isEmpty()) return
        connection.prepareStatement(
            """
            INSERT INTO staging_contribution
                (sub_id, cmte_id, contributor_name, city, state, zip_code, employer, occupation,
                 transaction_dt, transaction_amt, source, ingest_run_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            for (result in results) {
                statement.setString(1, result.subId.toString())
                statement.setString(2, result.committeeId)
                statement.setString(3, result.contributorName?.clip(200))
                statement.setString(4, result.contributorCity?.clip(30))
                statement.setString(5, result.contributorState?.clip(2))
                statement.setString(6, result.contributorZip?.clip(9))
                statement.setString(7, result.contributorEmployer?.clip(38))
                statement.setString(8, result.contributorOccupation?.clip(38))
                statement.setString(9, result.contributionReceiptDate?.let(::toStagingDate))
                statement.setBigDecimal(10, result.contributionReceiptAmount?.toBigDecimal())
                statement.setString(11, SOURCE)
                statement.setLong(12, runId)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /**
     * Deletes [runId]'s own rows from `staging_contribution` — called on
     * failure so a retried run doesn't pile orphans on top of orphans (PR-180).
     *
     * @param connection an open connection; not closed by this method
     * @param runId the `ingest_run` id whose staging rows to remove
     */
    private fun deleteStagingForRun(connection: Connection, runId: Long) {
        connection.prepareStatement("DELETE FROM staging_contribution WHERE ingest_run_id = ?").use { statement ->
            statement.setLong(1, runId)
            statement.executeUpdate()
        }
    }

    /**
     * Converts FEC's ISO date (`2026-03-15` or `2026-03-15T00:00:00+00:00`)
     * to the bulk staging format (`MMddyyyy`), so
     * [CanonicalLoader.loadContributions]'s `STR_TO_DATE(..., '%m%d%Y')`
     * works unchanged for rows from either source.
     *
     * @return the date formatted `MMddyyyy`
     */
    private fun toStagingDate(isoDate: String): String =
        LocalDate.parse(isoDate.take(10)).format(STAGING_DATE_FORMAT)

    /** Trims, truncates to [max] characters, and converts blank to `null` — matching `FecBulkParser`'s clip convention so both sources normalize free text the same way. */
    private fun String.clip(max: Int): String? = trim().take(max).ifEmpty { null }

    /**
     * Opens a connection via [DbConfig.openConnection] (PR-190) — no LOCAL
     * INFILE needed; incremental API loads are too small to need bulk loading.
     *
     * @return a new open [Connection]
     */
    private fun connect(): Connection = dbConfig.openConnection()
}
