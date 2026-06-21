package com.campaignfinances.pipeline.ingestion

import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement

/**
 * Per-file row counts for one ingest run.
 *
 * @property loaded rows successfully bulk-loaded into staging
 * @property bad malformed rows that were skipped (and logged)
 */
data class FileCounts(val loaded: Long, val bad: Long)

/**
 * Records pipeline executions in the `ingest_run` table.
 *
 * Every ingest gets a row: [start] inserts it with status RUNNING (the column
 * default) and [finish] stamps the end time, final status, and per-file row
 * counts. This is the pipeline's provenance/audit trail — staging rows carry
 * the run id, and operators can see what every run did and whether it succeeded.
 */
object IngestRunRepository {

    /**
     * Inserts a new RUNNING ingest_run row and returns its generated id.
     *
     * @param connection an open connection; not closed by this method
     * @param source which adapter is running, e.g. `fec-bulk`
     * @return the auto-increment id of the new row
     */
    fun start(connection: Connection, source: String): Long {
        val insertSql = "INSERT INTO ingest_run (source) VALUES (?)"

        // RETURN_GENERATED_KEYS asks JDBC to hand back the auto-increment id
        // created by the INSERT; .use closes the statement even on exceptions.
        connection.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS).use { statement ->
            statement.setString(1, source)
            statement.executeUpdate()
            return generatedKey(statement)
        }
    }

    /**
     * Reads the auto-increment id produced by an INSERT that was prepared with
     * [Statement.RETURN_GENERATED_KEYS].
     *
     * @throws IllegalStateException if the driver returned no key
     */
    private fun generatedKey(statement: PreparedStatement): Long {
        statement.generatedKeys.use { keys ->
            check(keys.next()) { "no generated key for ingest_run" }
            return keys.getLong(1)
        }
    }

    /**
     * Marks a run as finished.
     *
     * @param connection an open connection; not closed by this method
     * @param runId the id returned by [start]
     * @param status `SUCCESS` or `FAILED`
     * @param counts per-file results, stored as JSON in `row_counts`
     *   (note: MySQL's JSON type re-orders object keys alphabetically)
     */
    fun finish(connection: Connection, runId: Long, status: String, counts: Map<String, FileCounts>) {
        val updateSql = "UPDATE ingest_run SET finished_at = NOW(), status = ?, row_counts = ? WHERE id = ?"

        connection.prepareStatement(updateSql).use { statement ->
            statement.setString(1, status)
            statement.setString(2, countsJson(counts))
            statement.setLong(3, runId)
            statement.executeUpdate()
        }
    }

    /**
     * Renders counts as a JSON object by hand, e.g.
     * `{"cn":{"loaded":8037,"bad":0},...}`. Keys are FEC file keys and values
     * are plain numbers, so no escaping is needed — not worth a JSON library
     * dependency for this one string.
     */
    private fun countsJson(counts: Map<String, FileCounts>): String =
        counts.entries.joinToString(",", prefix = "{", postfix = "}") { (key, c) ->
            "\"$key\":{\"loaded\":${c.loaded},\"bad\":${c.bad}}"
        }
}
