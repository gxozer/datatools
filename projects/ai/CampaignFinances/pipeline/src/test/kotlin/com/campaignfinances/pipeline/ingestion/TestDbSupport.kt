package com.campaignfinances.pipeline.ingestion

import java.sql.Connection

/**
 * Wipes every pipeline table.
 *
 * `@Container @JvmStatic` MySQL instances are shared across all test methods
 * in a class (started once, not per test), so without this, data inserted by
 * one test method is still present when the next test method runs against
 * the same database — silently turning idempotent upserts into no-ops and
 * breaking row-count assertions that assume a clean schema. Call this from
 * `@BeforeEach` after [com.campaignfinances.pipeline.db.Migrator.migrate].
 *
 * Foreign key checks are disabled around the truncates so table order doesn't
 * matter; this is test-only, never run against a real environment.
 */
internal fun Connection.truncateAllPipelineTables() {
    createStatement().use { statement ->
        statement.execute("SET FOREIGN_KEY_CHECKS = 0")
        for (table in listOf(
            "donor_link", "contribution", "candidate_committee", "donor", "candidate", "committee", "ingest_run",
            "staging_candidate", "staging_committee", "staging_linkage", "staging_contribution",
        )) {
            statement.execute("TRUNCATE TABLE $table")
        }
        statement.execute("SET FOREIGN_KEY_CHECKS = 1")
    }
}

/** Runs [sql] and returns the first column of its first row as a `Long` — for one-shot `SELECT COUNT(*)`-shaped test assertions. */
internal fun Connection.queryLong(sql: String): Long = createStatement().use { statement ->
    statement.executeQuery(sql).use { rs -> rs.next(); rs.getLong(1) }
}

/** Runs [sql] and returns the first column of its first row as a `String?` — for one-shot single-value test assertions. */
internal fun Connection.queryString(sql: String): String? = createStatement().use { statement ->
    statement.executeQuery(sql).use { rs -> rs.next(); rs.getString(1) }
}
