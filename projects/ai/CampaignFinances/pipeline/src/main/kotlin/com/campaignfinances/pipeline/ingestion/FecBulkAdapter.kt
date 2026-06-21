package com.campaignfinances.pipeline.ingestion

import com.campaignfinances.pipeline.db.DbConfig
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager

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
 * fail the run. Any *exception* (network, database) marks the run FAILED and
 * rethrows.
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
            val counts = LinkedHashMap<String, FileCounts>()
            try {
                // Files are loaded one at a time, in FecBulkFileType.entries order;
                // `counts` accumulates as each one finishes so that if a later file
                // fails, the catch block below can still report what did succeed.
                for (type in FecBulkFileType.entries) {
                    val txt = localDir?.resolve(type.txtName) ?: downloader.fetch(cycle, type)
                    counts[type.key] = loadFile(connection, type, txt, runId)
                }
                IngestRunRepository.finish(connection, runId, "SUCCESS", counts)
                return IngestSummary(runId, counts)
            } catch (e: Exception) {
                // Record whatever was completed before the failure, then rethrow so
                // the caller (CLI) sees the error and exits non-zero.
                IngestRunRepository.finish(connection, runId, "FAILED", counts)
                throw e
            }
        }
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

        // Bad rows are reported through this callback rather than thrown: a few
        // malformed rows in a 25M-row file is expected, not exceptional. We
        // always count every one (for the FileCounts returned below) but only
        // print the first LOGGED_BAD_ROWS_PER_FILE, so a file with thousands of
        // bad rows doesn't flood `out` — the printed ones are just a sample to
        // eyeball, the count is what's authoritative.
        var badCount = 0L
        val logBadRow = { line: String, reason: String ->
            badCount++
            if (badCount <= LOGGED_BAD_ROWS_PER_FILE) {
                out.appendLine("[${type.key}] bad row ($reason): ${line.take(120)}")
            }
        }

        // FEC bulk files are not guaranteed clean UTF-8; ISO-8859-1 maps every
        // byte to a character, so reading never throws on odd encodings.
        val loadedCount = txt.toFile().useLines(StandardCharsets.ISO_8859_1) { lines ->
            val goodRows = parseGoodRows(lines, type, logBadRow)
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

    /** Opens a LOCAL-INFILE-enabled connection (bulk loads only, per PR-164). */
    private fun connect(): Connection =
        DriverManager.getConnection(dbConfig.urlWithLocalInfile, dbConfig.user, dbConfig.password)
}
