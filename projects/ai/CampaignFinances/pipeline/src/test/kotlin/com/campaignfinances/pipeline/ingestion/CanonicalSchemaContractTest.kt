package com.campaignfinances.pipeline.ingestion

import com.campaignfinances.pipeline.db.DbConfig
import com.campaignfinances.pipeline.db.Migrator
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Asserts the canonical schema's column-nullability contract directly via
 * `information_schema.columns`, independent of any insert.
 *
 * Real FEC bulk data can leave a "required-looking" field blank (e.g. a
 * candidate with no `CAND_NAME`) — [CanonicalLoader] must filter those rows
 * out before insert rather than let a `NOT NULL` violation fail the whole
 * run (PR-154 found exactly this with `candidate.name`/`office`). This test
 * pins the contract those filters depend on, in one place, so a future
 * migration that loosens or tightens a column's nullability without an
 * accompanying loader review gets caught here — rather than only in
 * production with real data (docs/TEST_PLAN_PHASE1.md §4.6).
 */
@Testcontainers
class CanonicalSchemaContractTest {

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")

        /** `(table, column) -> IS_NULLABLE`, loaded once for the whole class. */
        private lateinit var nullableByColumn: Map<Pair<String, String>, Boolean>

        @JvmStatic
        @BeforeAll
        fun loadSchema() {
            val config = DbConfig(mysql.jdbcUrl, mysql.username, mysql.password)
            Migrator(config).migrate()
            DriverManager.getConnection(config.url, config.user, config.password).use { connection ->
                connection.prepareStatement(
                    """
                    SELECT TABLE_NAME, COLUMN_NAME, IS_NULLABLE FROM information_schema.columns
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME IN ('candidate', 'committee', 'candidate_committee', 'contribution')
                    """.trimIndent(),
                ).use { statement ->
                    statement.executeQuery().use { rs ->
                        val map = mutableMapOf<Pair<String, String>, Boolean>()
                        while (rs.next()) {
                            val key = rs.getString("TABLE_NAME") to rs.getString("COLUMN_NAME")
                            map[key] = rs.getString("IS_NULLABLE") == "YES"
                        }
                        nullableByColumn = map
                    }
                }
            }
        }
    }

    /**
     * One row per canonical column (migrations V2, V3, V4, V6). Surrogate
     * `id` columns are included too — they're trivially `NOT NULL`, but
     * listing them keeps this the single complete inventory of every column
     * on these 4 tables, not just the ones a reader might guess to check.
     */
    @ParameterizedTest(name = "{0}.{1} nullable={2}")
    @CsvSource(
        value = [
            "candidate,id,false",
            "candidate,fec_candidate_id,false",
            "candidate,name,false",
            "candidate,office,false",
            "candidate,party,true",
            "candidate,state,true",
            "candidate,district,true",
            "committee,id,false",
            "committee,fec_committee_id,false",
            "committee,name,true",
            "committee,type,true",
            "committee,designation,true",
            "candidate_committee,candidate_id,false",
            "candidate_committee,committee_id,false",
            "candidate_committee,linkage_type,false",
            "contribution,id,false",
            "contribution,source,false",
            "contribution,source_record_id,false",
            "contribution,amount,false",
            "contribution,contribution_date,true",
            "contribution,donor_id,true",
            "contribution,committee_id,false",
        ],
    )
    fun `canonical column nullability matches the documented contract`(table: String, column: String, expectedNullable: Boolean) {
        val key = table to column
        assertTrue(key in nullableByColumn, "no such column: $table.$column")
        assertEquals(expectedNullable, nullableByColumn[key], "$table.$column")
    }
}
