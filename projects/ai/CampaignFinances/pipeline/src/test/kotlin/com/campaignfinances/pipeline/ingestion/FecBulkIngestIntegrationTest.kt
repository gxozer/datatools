package com.campaignfinances.pipeline.ingestion

import com.campaignfinances.pipeline.db.DbConfig
import com.campaignfinances.pipeline.db.Migrator
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.assertContains
import kotlin.test.assertEquals

@Testcontainers
class FecBulkIngestIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4").withCommand("mysqld", "--local-infile=1")
    }

    @Test
    fun `fixture files load into staging with provenance and bad-row tolerance`() {
        val config = DbConfig(mysql.jdbcUrl, mysql.username, mysql.password)
        Migrator(config).migrate()

        val fixtures = Path.of(javaClass.getResource("/fixtures/fec-bulk")!!.toURI())
        val summary = FecBulkAdapter(config, FecBulkDownloader(out = StringBuilder()), StringBuilder())
            .ingest(2026, localDir = fixtures)

        assertEquals(FileCounts(loaded = 3, bad = 1), summary.files["cn"])
        assertEquals(FileCounts(loaded = 3, bad = 1), summary.files["cm"])
        assertEquals(FileCounts(loaded = 3, bad = 1), summary.files["ccl"])
        assertEquals(FileCounts(loaded = 3, bad = 2), summary.files["indiv"])

        DriverManager.getConnection(config.url, config.user, config.password).use { conn ->
            fun queryLong(sql: String): Long = conn.createStatement().use { st ->
                st.executeQuery(sql).use { rs -> rs.next(); rs.getLong(1) }
            }

            // counts and provenance on every staging row
            for (type in FecBulkFileType.entries) {
                assertEquals(3, queryLong("SELECT COUNT(*) FROM ${type.stagingTable}"), type.stagingTable)
                assertEquals(
                    3,
                    queryLong(
                        "SELECT COUNT(*) FROM ${type.stagingTable} " +
                            "WHERE source = 'fec-bulk' AND ingest_run_id = ${summary.runId}",
                    ),
                    "${type.stagingTable} provenance",
                )
            }

            // sub_id present on every contribution row
            assertEquals(0, queryLong("SELECT COUNT(*) FROM staging_contribution WHERE sub_id IS NULL OR sub_id = ''"))
            // amounts loaded correctly, including the negative refund: 500 - 250 + 3300
            assertEquals(3550, queryLong("SELECT CAST(SUM(transaction_amt) AS SIGNED) FROM staging_contribution"))
            // blank transaction date stored as NULL
            assertEquals(1, queryLong("SELECT COUNT(*) FROM staging_contribution WHERE transaction_dt IS NULL"))

            // ingest_run records success and per-file counts including bad rows
            conn.createStatement().use { st ->
                st.executeQuery("SELECT status, row_counts FROM ingest_run WHERE id = ${summary.runId}").use { rs ->
                    rs.next()
                    assertEquals("SUCCESS", rs.getString("status"))
                    // MySQL JSON normalizes key order, so assert keys alphabetically
                    val json = rs.getString("row_counts").replace(" ", "")
                    assertContains(json, "\"indiv\":{\"bad\":2,\"loaded\":3}")
                    assertContains(json, "\"cn\":{\"bad\":1,\"loaded\":3}")
                }
            }
        }
    }
}
