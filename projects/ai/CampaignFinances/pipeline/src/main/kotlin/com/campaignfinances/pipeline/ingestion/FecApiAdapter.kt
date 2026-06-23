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
import java.sql.DriverManager
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
 *   production default is provided, tests inject a `MockEngine`-backed client
 * @param out where progress and the watermark line are printed
 */
class FecApiAdapter(
    private val dbConfig: DbConfig,
    apiConfig: FecApiConfig,
    httpClient: HttpClient = defaultHttpClient(),
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
     * Runs one incremental top-up.
     *
     * @param cycle the two-year federal election cycle; used only as the
     *   watermark fallback when no prior successful `fec-api` run exists
     * @param localDir ignored — the API has no local files to read from;
     *   present only because this adapter shares the [BulkIngestRunner]
     *   interface with [FecBulkAdapter]
     */
    override fun ingest(cycle: Int, localDir: Path?): IngestSummary = runBlocking {
        connect().use { connection ->
            val runId = IngestRunRepository.start(connection, SOURCE)
            try {
                val minDate = watermark(connection, cycle)
                out.appendLine("fetching since $minDate")
                val staged = fetchAndStage(connection, runId, minDate)
                val canonical = CanonicalLoader(connection).loadContributions(runId)
                IngestRunRepository.finish(connection, runId, "SUCCESS", mapOf("indiv" to staged, "contribution" to canonical))
                IngestSummary(runId, mapOf("indiv" to staged))
            } catch (e: Exception) {
                IngestRunRepository.finish(connection, runId, "FAILED", emptyMap())
                throw e
            }
        }
    }

    /**
     * The last successful `fec-api` run's `finished_at` date, or [cycle]'s
     * first day if this is the first `fec-api` run ever (nothing to top up
     * before the cycle starts).
     */
    private fun watermark(connection: Connection, cycle: Int): String {
        connection.prepareStatement(
            "SELECT MAX(finished_at) FROM ingest_run WHERE source = ? AND status = 'SUCCESS'",
        ).use { statement ->
            statement.setString(1, SOURCE)
            statement.executeQuery().use { rs ->
                rs.next()
                val lastFinished = rs.getTimestamp(1)
                return if (lastFinished != null) {
                    lastFinished.toLocalDateTime().toLocalDate().format(WATERMARK_FORMAT)
                } else {
                    LocalDate.of(cycle - 1, 1, 1).format(WATERMARK_FORMAT)
                }
            }
        }
    }

    /**
     * Pages through every contribution since [minDate], inserting each page
     * into `staging_contribution` as it arrives. Stops once the API reports
     * no further pages.
     */
    private suspend fun fetchAndStage(connection: Connection, runId: Long, minDate: String): FileCounts {
        var cursor: LastIndexes? = null
        var loaded = 0L
        while (true) {
            val page = apiClient.fetchPage(minDate, cursor)
            insertStaging(connection, runId, page.results)
            loaded += page.results.size
            if (page.pagination.page >= page.pagination.pages || page.results.isEmpty()) break
            cursor = page.pagination.lastIndexes
        }
        out.appendLine("[fec-api] loaded $loaded contributions into staging_contribution")
        // The API's response shape is well-typed JSON (unlike pipe-delimited
        // bulk text), so there is no equivalent of a "malformed row" here —
        // bad is always 0 at this stage. Rows whose committee can't be
        // resolved are still caught downstream by loadContributions.
        return FileCounts(loaded, bad = 0)
    }

    /** Batches one page's results into a single multi-row INSERT. */
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
                statement.setString(3, result.contributorName)
                statement.setString(4, result.contributorCity)
                statement.setString(5, result.contributorState)
                statement.setString(6, result.contributorZip)
                statement.setString(7, result.contributorEmployer)
                statement.setString(8, result.contributorOccupation)
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
     * Converts FEC's ISO date (`2026-03-15` or `2026-03-15T00:00:00+00:00`)
     * to the bulk staging format (`MMddyyyy`), so
     * [CanonicalLoader.loadContributions]'s `STR_TO_DATE(..., '%m%d%Y')`
     * works unchanged for rows from either source.
     */
    private fun toStagingDate(isoDate: String): String =
        LocalDate.parse(isoDate.take(10)).format(STAGING_DATE_FORMAT)

    private fun connect(): Connection = DriverManager.getConnection(dbConfig.url, dbConfig.user, dbConfig.password)
}
