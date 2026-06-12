package com.campaignfinances.pipeline

import com.campaignfinances.pipeline.db.DbConfig
import com.campaignfinances.pipeline.db.Migrator
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import kotlin.test.assertTrue

@Testcontainers
class FlywayMigrationTest {

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }

    @Test
    fun `all migrations apply cleanly to an empty database`() {
        val config = DbConfig(mysql.jdbcUrl, mysql.username, mysql.password)

        val result = Migrator(config).migrate()

        assertTrue(result.success, "Flyway migration failed")

        val expectedTables = setOf(
            "ingest_run", "candidate", "committee", "candidate_committee",
            "donor", "donor_link", "contribution",
            "staging_candidate", "staging_committee", "staging_linkage", "staging_contribution",
        )

        DriverManager.getConnection(config.url, config.user, config.password).use { conn ->
            val actualTables = buildSet {
                conn.metaData.getTables(conn.catalog, null, "%", arrayOf("TABLE")).use { rs ->
                    while (rs.next()) add(rs.getString("TABLE_NAME").lowercase())
                }
            }
            val missing = expectedTables - actualTables
            assertTrue(missing.isEmpty(), "Missing tables after migration: $missing")
        }
    }
}
