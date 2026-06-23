package com.campaignfinances.pipeline.ingestion

import com.campaignfinances.pipeline.db.DbConfig
import com.campaignfinances.pipeline.db.Migrator
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.DriverManager
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Integration test for [FecApiAdapter]: real Testcontainers MySQL, mocked
 * HTTP (no real network call to api.open.fec.gov), exercising the full
 * staging-then-canonical path and the watermark mechanism across separate
 * runs (docs/TEST_PLAN_PHASE1.md).
 */
@Testcontainers
class FecApiAdapterIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }

    /**
     * Migrates and wipes the schema, then seeds the one committee
     * [FecApiAdapter] depends on already existing — it only ever resolves
     * `committee_id` via [CanonicalLoader.loadContributions], never creates
     * a committee itself, since a prior bulk load is its documented
     * prerequisite (docs/TDS_PHASE1.md §3).
     */
    @BeforeEach
    fun setUp() {
        val config = DbConfig(mysql.jdbcUrl, mysql.username, mysql.password)
        Migrator(config).migrate()
        DriverManager.getConnection(config.url, config.user, config.password).use { connection ->
            connection.truncateAllPipelineTables()
            connection.prepareStatement(
                "INSERT INTO committee (fec_committee_id, name, type, designation) VALUES ('C00386987', 'TED LIEU FOR CONGRESS', 'H', 'P')",
            ).use { it.executeUpdate() }
        }
    }

    /** One fixed page; the mock does not filter by `min_date` — these tests exercise the adapter's own mechanics, not real FEC API filtering. */
    private fun onePageJson(subId: Long) = """
        {
          "results": [
            {
              "sub_id": $subId,
              "committee_id": "C00386987",
              "contributor_name": "DOE, JANE",
              "contributor_city": "ANYTOWN",
              "contributor_state": "CA",
              "contributor_zip": "900121234",
              "contributor_employer": "ANY EMPLOYER",
              "contributor_occupation": "ANY OCCUPATION",
              "contribution_receipt_date": "2026-03-15T00:00:00+00:00",
              "contribution_receipt_amount": 250.00
            }
          ],
          "pagination": { "page": 1, "pages": 1, "last_indexes": null }
        }
    """.trimIndent()

    private fun httpClientReturning(json: String): HttpClient {
        val engine = MockEngine { _ ->
            respond(content = json, status = HttpStatusCode.OK, headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))
        }
        return HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
    }

    @Test
    fun `ingest stages and normalizes a contribution, watermarking the first run to the cycle start`() {
        val config = DbConfig(mysql.jdbcUrl, mysql.username, mysql.password)
        val out = StringBuilder()
        val adapter = FecApiAdapter(config, FecApiConfig("test-key"), httpClientReturning(onePageJson(4041520261234567890)), out)

        val summary = adapter.ingest(2026, localDir = null)

        assertContains(out.toString(), "fetching since 2025-01-01")
        assertEquals(FileCounts(1, 0), summary.files["indiv"])

        DriverManager.getConnection(config.url, config.user, config.password).use { connection ->
            assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM staging_contribution WHERE source = 'fec-api'"))
            assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM contribution WHERE source = 'fec-api'"))
            assertEquals(
                "2026-03-15",
                connection.queryString("SELECT contribution_date FROM contribution WHERE source_record_id = '4041520261234567890'"),
            )
        }
    }

    @Test
    fun `re-running fec-api with the same data is idempotent and the watermark advances to the prior run's finish date`() {
        val config = DbConfig(mysql.jdbcUrl, mysql.username, mysql.password)
        val firstAdapter = FecApiAdapter(config, FecApiConfig("test-key"), httpClientReturning(onePageJson(4041520261234567890)), StringBuilder())
        val first = firstAdapter.ingest(2026, localDir = null)

        val firstRunFinishedDate = DriverManager.getConnection(config.url, config.user, config.password).use { connection ->
            connection.queryString("SELECT DATE_FORMAT(finished_at, '%Y-%m-%d') FROM ingest_run WHERE id = ${first.runId}")
        }

        val secondOut = StringBuilder()
        val secondAdapter = FecApiAdapter(config, FecApiConfig("test-key"), httpClientReturning(onePageJson(4041520261234567890)), secondOut)
        secondAdapter.ingest(2026, localDir = null)

        assertContains(secondOut.toString(), "fetching since $firstRunFinishedDate")

        DriverManager.getConnection(config.url, config.user, config.password).use { connection ->
            // Same sub_id, same source ('fec-api' on both runs) — must
            // upsert in place, not duplicate.
            assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM contribution WHERE source = 'fec-api'"))
        }
    }
}
