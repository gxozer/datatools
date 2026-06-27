package com.campaignfinances.pipeline.ingestion

import com.campaignfinances.pipeline.db.DbConfig
import mu.KotlinLogging
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

private val logger = KotlinLogging.logger {}

/**
 * Result of one bulk ingest.
 *
 * @property runId the `ingest_run` row recording this execution
 * @property files per-file loaded/bad counts, keyed by [FecBulkFileType.key]
 */
data class IngestSummary(val runId: Long, val files: Map<String, FileCounts>)

/**
 * Source adapter for FEC bulk files (docs/TDS_PHASE1.md §3).
 *
 * For each of the four file types it: obtains the file (download, or read from
 * `localDir` for tests/offline runs), parses every line, truncates the staging
 * table, and bulk-loads the good rows. Malformed rows are tolerated, logged
 * (first few per file), and counted into `ingest_run.row_counts` — they never
 * fail the run. Once all four staging tables are loaded, [CanonicalLoader]
 * normalizes them into the canonical schema (docs/TDS_PHASE1.md §4) in the
 * same run, and its per-stage counts are merged into the same
 * `ingest_run.row_counts`. Any *exception* (network, database) marks the run
 * FAILED and rethrows.
 *
 * Reference timing: the full 2026 cycle (25.3M rows incl. a 1.7 GB download)
 * loads in ~7 minutes on a dev laptop (PR-153).
 *
 * @param dbConfig connection settings; this adapter connects with
 *   [DbConfig.urlWithLocalInfile] because bulk loads need LOCAL INFILE
 * @param downloader fetches and caches the bulk files
 * @param out where per-file progress and bad-row samples are printed
 */
class FecBulkAdapter(
    private val dbConfig: DbConfig,
    private val downloader: FecBulkDownloader,
    private val out: Appendable = System.out,
) : BulkIngestRunner {

    companion object {
        /** Provenance label written to `source` on every staging row and ingest_run. */
        const val SOURCE = "fec-bulk"

        /** How many malformed rows to print per file before going quiet (counts continue). */
        private const val LOGGED_BAD_ROWS_PER_FILE = 5
    }

    /**
     * Runs the full bulk ingest for one cycle.
     *
     * @param cycle the two-year federal election cycle, e.g. 2026
     * @param localDir if non-null, read `.txt` files from here instead of downloading
     * @return per-file counts and the ingest_run id
     * @throws Exception any failure, after marking the run FAILED in ingest_run
     */
    override fun ingest(cycle: Int, localDir: Path?): IngestSummary {
        connect().use { connection ->
            val runId = IngestRunRepository.start(connection, SOURCE)
            // LinkedHashMap keeps files in processing order in the JSON output.
            // File-stage counts (cn/cm/ccl/indiv) are kept separate from canonical
            // counts so IngestSummary.files keeps reporting only the bulk-file
            // stage, exactly as it did before canonical loading existed.
            val fileCounts = LinkedHashMap<String, FileCounts>()
            val canonicalCounts = LinkedHashMap<String, FileCounts>()
            try {
                // Files are loaded one at a time, in FecBulkFileType.entries order;
                // `fileCounts` accumulates as each one finishes so that if a later
                // file fails, the catch block below can still report what did succeed.
                for (type in FecBulkFileType.entries) {
                    val txt = localDir?.resolve(type.txtName) ?: downloader.fetch(cycle, type)
                    fileCounts[type.key] = loadFile(connection, type, txt, runId)
                }
                // Staging is fully loaded — normalize it into the canonical schema
                // (docs/TDS_PHASE1.md §4) before marking the run SUCCESS, so a
                // failure here still rolls into ingest_run as a FAILED run rather
                // than silently leaving staging un-normalized.
                loadCanonical(connection, runId, canonicalCounts)
                IngestRunRepository.finish(connection, runId, "SUCCESS", fileCounts + canonicalCounts)
                return IngestSummary(runId, fileCounts)
            } catch (e: Exception) {
                // Record whatever was completed before the failure, then rethrow so
                // the caller (CLI) sees the error and exits non-zero.
                IngestRunRepository.finish(connection, runId, "FAILED", fileCounts + canonicalCounts)
                throw e
            }
        }
    }

    /**
     * Runs [CanonicalLoader] over this run's staging rows and writes its
     * per-stage counts into [canonicalCounts], keyed by canonical table name
     * (`candidate`, `committee`, `candidate_committee`, `contribution`) —
     * distinct from the bulk file keys (`cn`, `cm`, `ccl`, `indiv`). Order
     * matters: candidates and committees must land before the tables that
     * reference them by surrogate key.
     */
    private fun loadCanonical(connection: Connection, runId: Long, canonicalCounts: LinkedHashMap<String, FileCounts>) {
        val canonical = CanonicalLoader(connection)
        canonicalCounts["candidate"] = canonical.loadCandidates(runId)
        canonicalCounts["committee"] = canonical.loadCommittees(runId)
        canonicalCounts["candidate_committee"] = canonical.loadCandidateCommittees(runId)
        canonicalCounts["contribution"] = canonical.loadContributions(runId)
    }

    /**
     * Parses and loads one file into its staging table.
     *
     * The file is streamed line by line ([kotlin.io.useLines]) and good rows go
     * to the loader as a lazy [Sequence] — at no point are 25M rows in memory.
     *
     * @param connection open DB connection (LOCAL-INFILE-enabled, from [connect])
     * @param type which bulk file this is, e.g. column layout and staging table
     * @param txt the local `.txt` path to read (downloaded, or from `localDir`)
     * @param runId the `ingest_run` row this file's counts get attributed to
     * @return loaded/bad counts for this file
     */
    private fun loadFile(connection: Connection, type: FecBulkFileType, txt: Path, runId: Long): FileCounts {
        val loader = StagingLoader(connection)
        loader.truncate(type)

        // Bad rows are reported through a callback rather than thrown: a few
        // malformed rows in a 25M-row file is expected, not exceptional. We
        // always count every one (for the FileCounts returned below) but only
        // print the first LOGGED_BAD_ROWS_PER_FILE, so a file with thousands of
        // bad rows doesn't flood `out` — the printed ones are just a sample to
        // eyeball, the count is what's authoritative.
        var badCount = 0L

        // FEC bulk files are not guaranteed clean UTF-8; ISO-8859-1 maps every
        // byte to a character, so reading never throws on odd encodings.
        val loadedCount = txt.toFile().useLines(StandardCharsets.ISO_8859_1) { lines ->
            val goodRows = parseGoodRows(lines, type) { line: String, reason: String ->
                badCount++
                reportBadRow(type, line, reason, badCount)
            }
            loader.load(type, goodRows, SOURCE, runId)
        }

        out.appendLine("[${type.key}] loaded $loadedCount rows into ${type.stagingTable} ($badCount bad)")
        return FileCounts(loadedCount, badCount)
    }

    /**
     * Parses each raw line against [type]'s column layout and lazily yields the
     * successfully parsed rows, in order. Blank lines are skipped silently;
     * malformed lines are reported through [onBadRow] instead of being yielded,
     * so they never reach the loader.
     *
     * This stays a [Sequence] (rather than building a [List]) so [loadFile] can
     * stream straight from disk into the database without holding all 25M rows
     * in memory at once.
     *
     * @param lines raw lines from the bulk file, in file order
     * @param type which file type's column layout to parse against
     * @param onBadRow called with the offending line and the parser's rejection reason
     * @return a lazy sequence of successfully parsed rows
     */
    private fun parseGoodRows(
        lines: Sequence<String>,
        type: FecBulkFileType,
        onBadRow: (line: String, reason: String) -> Unit,
    ): Sequence<List<String?>> = sequence {
        for (line in lines) {
            if (line.isBlank()) continue
            val result = FecBulkParser.parse(type, line)
            when (result) {
                is ParseResult.Ok -> yield(result.values)
                is ParseResult.Bad -> onBadRow(line, result.reason)
            }
        }
    }

    /**
     * Logs one bad row to [logger] at WARN level and echoes it to [out], but
     * only for the first [LOGGED_BAD_ROWS_PER_FILE] per file so a heavily-
     * corrupted file does not flood output. The count continues accurately
     * regardless.
     *
     * @param type the file type, used for the log prefix (e.g. `[indiv]`)
     * @param line the raw offending line, truncated to 120 characters
     * @param reason the parser's rejection reason
     * @param badCount the running total of bad rows, used to gate printing
     */
    private fun reportBadRow(type: FecBulkFileType, line: String, reason: String, badCount: Long) {
        if (badCount <= LOGGED_BAD_ROWS_PER_FILE) {
            logger.warn { "[${type.key}] bad row ($reason): ${line.take(120)}" }
            out.appendLine("[${type.key}] bad row ($reason): ${line.take(120)}")
        }
    }

    /** Opens a LOCAL-INFILE-enabled connection (bulk loads only, per PR-164). */
    private fun connect(): Connection =
        DriverManager.getConnection(dbConfig.urlWithLocalInfile, dbConfig.user, dbConfig.password)
}
