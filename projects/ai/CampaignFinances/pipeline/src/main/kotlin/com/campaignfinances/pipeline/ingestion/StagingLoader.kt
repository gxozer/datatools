package com.campaignfinances.pipeline.ingestion

import java.nio.file.Files
import java.sql.Connection
import kotlin.io.path.bufferedWriter

/**
 * Loads parsed staging rows via `LOAD DATA LOCAL INFILE` (docs/TDS_PHASE1.md §7).
 *
 * Why LOAD DATA instead of batched INSERTs: the contributions file alone is
 * ~25M rows per cycle, and MySQL's bulk path is an order of magnitude faster.
 * Rows are written to a temporary tab-separated file first, then handed to the
 * server in a single statement.
 *
 * **Connection requirement:** the connection must have been opened with
 * `allowLoadLocalInfile=true` (see [com.campaignfinances.pipeline.db.DbConfig.urlWithLocalInfile]).
 * The capability is enabled only on this bulk-load path, per the security
 * decision on PR-164; the server side needs `--local-infile=1` (set in
 * docker-compose.yml).
 *
 * @param connection an open, LOCAL-INFILE-enabled connection; not closed here
 */
class StagingLoader(private val connection: Connection) {

    /**
     * Empties a staging table before a fresh load. Staging is per-run scratch
     * space — each bulk load replaces the previous snapshot entirely.
     */
    fun truncate(type: FecBulkFileType) {
        connection.createStatement().use { statement ->
            statement.executeUpdate("TRUNCATE TABLE ${type.stagingTable}")
        }
    }

    /**
     * Writes rows to a temp TSV and bulk-loads them into the staging table.
     *
     * @param type decides the target table and column list
     * @param rows staging-ordered values per row (see [FecBulkParser]); a lazy
     *   [Sequence] so the caller can stream millions of rows without holding
     *   them in memory
     * @param source provenance label stamped on every row (e.g. `fec-bulk`)
     * @param ingestRunId the ingest_run id stamped on every row
     * @return the number of rows the server reports as loaded
     */
    fun load(type: FecBulkFileType, rows: Sequence<List<String?>>, source: String, ingestRunId: Long): Long {
        val tsv = Files.createTempFile("cf-${type.key}-", ".tsv")
        try {
            writeTsv(tsv, rows)
            return loadTsv(tsv, type, source, ingestRunId)
        } finally {
            // The temp file can be several GB for contributions — always clean up.
            Files.deleteIfExists(tsv)
        }
    }

    /** Streams rows into [tsv], one escaped tab-separated line per row. */
    private fun writeTsv(tsv: java.nio.file.Path, rows: Sequence<List<String?>>) {
        tsv.bufferedWriter().use { writer ->
            for (row in rows) {
                writer.appendLine(row.joinToString("\t") { escape(it) })
            }
        }
    }

    /** Executes the LOAD DATA statement for [tsv] and returns the loaded count. */
    private fun loadTsv(tsv: java.nio.file.Path, type: FecBulkFileType, source: String, ingestRunId: Long): Long {
        val columns = type.stagingColumns.joinToString(", ")
        // The SET clause stamps provenance columns that are not in the file itself.
        val sql = """
            LOAD DATA LOCAL INFILE '${tsv.toAbsolutePath().toString().replace("\\", "\\\\")}'
            INTO TABLE ${type.stagingTable}
            FIELDS TERMINATED BY '\t' ESCAPED BY '\\'
            LINES TERMINATED BY '\n'
            ($columns)
            SET source = ?, ingest_run_id = ?
        """.trimIndent()

        connection.prepareStatement(sql).use { statement ->
            statement.setString(1, source)
            statement.setLong(2, ingestRunId)
            return statement.executeUpdate().toLong()
        }
    }

    /**
     * Escapes one field for the TSV: null becomes MySQL's `\N` (SQL NULL) and
     * the four characters that would corrupt the format (backslash, tab,
     * newline, carriage return) are backslash-escaped.
     */
    private fun escape(value: String?): String {
        if (value == null) return "\\N"
        return value
            .replace("\\", "\\\\")
            .replace("\t", "\\t")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
