package com.campaignfinances.pipeline.cli

import com.campaignfinances.pipeline.db.DbConfig
import com.campaignfinances.pipeline.db.Migrator
import com.campaignfinances.pipeline.ingestion.queryLong
import com.campaignfinances.pipeline.ingestion.truncateAllPipelineTables
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Wires the production `IngestCommand(dbConfig)` constructor — not the
 * fake-[com.campaignfinances.pipeline.ingestion.BulkIngestRunner] constructor
 * [IngestCommandTest] uses — to a real
 * [com.campaignfinances.pipeline.ingestion.FecBulkAdapter] against a real
 * Testcontainers MySQL. [IngestCommandTest] proves the CLI's argument
 * parsing in isolation; this proves the CLI layer actually drives a real
 * ingest end to end: real exit code, real printed summary line, against
 * real fixture-derived counts (docs/TEST_PLAN_PHASE1.md §4.8).
 */
@Testcontainers
class IngestCommandComponentTest {

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4").withCommand("mysqld", "--local-infile=1")
    }

    @BeforeEach
    fun resetDatabase() {
        val config = DbConfig(mysql.jdbcUrl, mysql.username, mysql.password)
        Migrator(config).migrate()
        DriverManager.getConnection(config.url, config.user, config.password).use { it.truncateAllPipelineTables() }
    }

    @Test
    fun `ingest --source=fec-bulk drives a real FecBulkAdapter and prints the real fixture counts`() {
        val config = DbConfig(mysql.jdbcUrl, mysql.username, mysql.password)
        val fixtures = Path.of(javaClass.getResource("/fixtures/fec-bulk")!!.toURI())
        val out = StringBuilder()
        val cmd = IngestCommand(config, out)

        // Fixture totals (see FecBulkIngestIntegrationTest): cn/cm/ccl are
        // (loaded=3, bad=1) each, indiv is (loaded=3, bad=2) — summed by
        // IngestCommand into one printed line, exactly as a real CLI run
        // would show it.
        val exitCode = cmd.run(listOf("--source=fec-bulk", "--cycle=2026", "--dir=$fixtures"))

        assertEquals(0, exitCode)
        assertContains(out.toString(), "complete: 12 rows loaded, 5 bad rows skipped")

        DriverManager.getConnection(config.url, config.user, config.password).use { connection ->
            assertEquals(3, connection.queryLong("SELECT COUNT(*) FROM candidate"))
            assertEquals(3, connection.queryLong("SELECT COUNT(*) FROM contribution"))
        }
    }
}
