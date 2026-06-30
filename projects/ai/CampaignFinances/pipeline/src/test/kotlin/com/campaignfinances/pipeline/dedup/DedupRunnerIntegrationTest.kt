package com.campaignfinances.pipeline.dedup

import com.campaignfinances.pipeline.db.DbConfig
import com.campaignfinances.pipeline.db.Migrator
import com.campaignfinances.pipeline.ingestion.queryLong
import com.campaignfinances.pipeline.ingestion.queryString
import com.campaignfinances.pipeline.ingestion.truncateAllPipelineTables
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Integration tests for [DedupRunner] against a real MySQL (Testcontainers),
 * verifying that staging rows are correctly grouped into donors and linked
 * via donor_link, including near-miss cases that must NOT merge (PR-156,
 * docs/TDS_PHASE1.md §5).
 */
@Testcontainers
class DedupRunnerIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }

    private lateinit var connection: Connection
    private lateinit var config: DbConfig
    private var committeeId: Long = 0

    @BeforeEach
    fun setUp() {
        config = DbConfig(mysql.jdbcUrl, mysql.username, mysql.password)
        Migrator(config).migrate()
        connection = DriverManager.getConnection(config.url, config.user, config.password)
        connection.truncateAllPipelineTables()
        committeeId = insertCommittee("C00000001")
    }

    // ── Merging ───────────────────────────────────────────────────────────────

    @Test
    fun `two contributions with same name, zip, and employer merge into one donor`() {
        insertContributionWithStaging("111", "SMITH, JOHN", "90210", "ACME")
        insertContributionWithStaging("222", "SMITH, JOHN", "90210", "ACME")

        val summary = DedupRunner(config).run()

        assertEquals(1, summary.donorsCreated)
        assertEquals(2, summary.linksCreated)
        assertEquals(0, summary.skipped)
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM donor"))
        assertEquals(2, connection.queryLong("SELECT COUNT(*) FROM donor_link"))
        assertEquals(2, connection.queryLong("SELECT COUNT(*) FROM contribution WHERE donor_id IS NOT NULL"))
    }

    @Test
    fun `match rule is name+zip5+employer when employer is present`() {
        insertContributionWithStaging("111", "SMITH, JOHN", "90210", "ACME")
        insertContributionWithStaging("222", "SMITH, JOHN", "90210", "ACME")
        DedupRunner(config).run()

        val rule = connection.queryString("SELECT DISTINCT match_rule FROM donor_link")
        assertEquals("name+zip5+employer", rule)
    }

    @Test
    fun `two contributions with same name and zip but blank employer merge into one donor`() {
        insertContributionWithStaging("111", "JONES, ANN", "10001", null)
        insertContributionWithStaging("222", "JONES, ANN", "10001", "")

        val summary = DedupRunner(config).run()

        assertEquals(1, summary.donorsCreated)
        assertEquals(2, summary.linksCreated)
    }

    @Test
    fun `match rule is name+zip5 (employer blank) when employer is absent`() {
        insertContributionWithStaging("111", "JONES, ANN", "10001", null)
        DedupRunner(config).run()

        val rule = connection.queryString("SELECT DISTINCT match_rule FROM donor_link")
        assertEquals("name+zip5 (employer blank)", rule)
    }

    @Test
    fun `name normalization merges contributions despite punctuation and suffix differences`() {
        // "SMITH JR, JOHN" and "SMITH, JOHN" should normalize to the same key
        insertContributionWithStaging("111", "SMITH JR, JOHN", "90210", "ACME")
        insertContributionWithStaging("222", "SMITH, JOHN", "90210", "ACME")

        val summary = DedupRunner(config).run()

        assertEquals(1, summary.donorsCreated)
        assertEquals(2, summary.linksCreated)
    }

    @Test
    fun `employer alias SELF-EMPLOYED merges with SELF EMPLOYED`() {
        insertContributionWithStaging("111", "DOE, JANE", "30301", "SELF-EMPLOYED")
        insertContributionWithStaging("222", "DOE, JANE", "30301", "SELF EMPLOYED")

        val summary = DedupRunner(config).run()

        assertEquals(1, summary.donorsCreated)
        assertEquals(2, summary.linksCreated)
    }

    // ── Non-merging (near-miss) ───────────────────────────────────────────────

    @Test
    fun `same name different zip5 must NOT merge`() {
        insertContributionWithStaging("111", "SMITH, JOHN", "90210", "ACME")
        insertContributionWithStaging("222", "SMITH, JOHN", "10001", "ACME")

        val summary = DedupRunner(config).run()

        assertEquals(2, summary.donorsCreated)
        assertEquals(2, summary.linksCreated)
    }

    @Test
    fun `same name same zip different named employers must NOT merge`() {
        insertContributionWithStaging("111", "SMITH, JOHN", "90210", "ACME CORP")
        insertContributionWithStaging("222", "SMITH, JOHN", "90210", "BIGCORP")

        val summary = DedupRunner(config).run()

        assertEquals(2, summary.donorsCreated)
        assertEquals(2, summary.linksCreated)
    }

    @Test
    fun `blank employer contribution is NOT merged into named employer group`() {
        insertContributionWithStaging("111", "SMITH, JOHN", "90210", "ACME")
        insertContributionWithStaging("222", "SMITH, JOHN", "90210", null)

        val summary = DedupRunner(config).run()

        // blank employer forms its own donor separate from ACME
        assertEquals(2, summary.donorsCreated)
        assertEquals(2, summary.linksCreated)
    }

    @Test
    fun `different last names must NOT merge`() {
        insertContributionWithStaging("111", "SMITH, JOHN", "90210", "ACME")
        insertContributionWithStaging("222", "JONES, JOHN", "90210", "ACME")

        val summary = DedupRunner(config).run()

        assertEquals(2, summary.donorsCreated)
    }

    // ── Skipping ──────────────────────────────────────────────────────────────

    @Test
    fun `contribution with blank name is skipped and donor_id stays null`() {
        insertContributionWithStaging("111", null, "90210", "ACME")

        val summary = DedupRunner(config).run()

        assertEquals(0, summary.donorsCreated)
        assertEquals(0, summary.linksCreated)
        assertEquals(1, summary.skipped)
        assertNull(connection.queryString("SELECT donor_id FROM contribution WHERE source_record_id = '111'"))
    }

    @Test
    fun `contribution with missing zip is skipped and donor_id stays null`() {
        insertContributionWithStaging("111", "SMITH, JOHN", null, "ACME")

        val summary = DedupRunner(config).run()

        assertEquals(0, summary.donorsCreated)
        assertEquals(1, summary.skipped)
        assertNull(connection.queryString("SELECT donor_id FROM contribution WHERE source_record_id = '111'"))
    }

    // ── Audit completeness ────────────────────────────────────────────────────

    @Test
    fun `every contribution with a donor_id has exactly one donor_link row`() {
        insertContributionWithStaging("111", "SMITH, JOHN", "90210", "ACME")
        insertContributionWithStaging("222", "JONES, ANN", "10001", null)
        DedupRunner(config).run()

        val withDonor = connection.queryLong("SELECT COUNT(*) FROM contribution WHERE donor_id IS NOT NULL")
        val linkCount = connection.queryLong("SELECT COUNT(*) FROM donor_link")
        assertEquals(withDonor, linkCount)
    }

    // ── Re-run determinism ────────────────────────────────────────────────────

    @Test
    fun `running dedup twice produces identical results`() {
        insertContributionWithStaging("111", "SMITH, JOHN", "90210", "ACME")
        insertContributionWithStaging("222", "JONES, ANN", "10001", null)

        val first = DedupRunner(config).run()
        val second = DedupRunner(config).run()

        assertEquals(first.donorsCreated, second.donorsCreated)
        assertEquals(first.linksCreated, second.linksCreated)
        assertEquals(first.skipped, second.skipped)
    }

    // ── donor canonical_name ──────────────────────────────────────────────────

    @Test
    fun `donor canonical_name is stored as LAST, FIRST in normalized form`() {
        insertContributionWithStaging("111", "smith, john paul", "90210", "ACME")
        DedupRunner(config).run()

        val canonicalName = connection.queryString("SELECT canonical_name FROM donor")
        assertEquals("SMITH, JOHN", canonicalName)
    }

    // ── Duplicate staging rows (PR-202) ──────────────────────────────────────

    @Test
    fun `duplicate staging rows for same sub_id produce exactly one donor_link row`() {
        // Simulate two FecApiAdapter runs inserting the same staging row
        insertStagingOnly("111", "SMITH, JOHN", "90210", "ACME")
        insertContributionOnly("111")
        insertStagingOnly("111", "SMITH, JOHN", "90210", "ACME") // duplicate staging

        val summary = DedupRunner(config).run()

        assertEquals(1, summary.donorsCreated)
        assertEquals(1, summary.linksCreated)
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM donor_link"))
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Inserts a committee row and returns its generated id.
     */
    private fun insertCommittee(fecCommitteeId: String): Long {
        connection.prepareStatement(
            "INSERT INTO committee (fec_committee_id, name, type, designation) VALUES (?, 'Test Committee', 'P', 'P')",
            java.sql.Statement.RETURN_GENERATED_KEYS,
        ).use { statement ->
            statement.setString(1, fecCommitteeId)
            statement.executeUpdate()
            statement.generatedKeys.use { keys ->
                keys.next()
                return keys.getLong(1)
            }
        }
    }

    /**
     * Inserts both a `staging_contribution` row and a matching `contribution`
     * row (joined by source + sub_id/source_record_id), the minimum setup the
     * runner needs to include a row.
     *
     * Source is fixed to `"test"` for all rows in this test class.
     */
    private fun insertContributionWithStaging(
        subId: String,
        contributorName: String?,
        zipCode: String?,
        employer: String?,
    ) {
        insertStagingOnly(subId, contributorName, zipCode, employer)
        insertContributionOnly(subId)
    }

    /**
     * Inserts only a `staging_contribution` row (no canonical contribution).
     * Use [insertContributionOnly] separately when you need to control how many
     * staging rows exist per canonical contribution (duplicate-staging tests).
     */
    private fun insertStagingOnly(
        subId: String,
        contributorName: String?,
        zipCode: String?,
        employer: String?,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO staging_contribution
                (sub_id, cmte_id, contributor_name, zip_code, employer, transaction_amt, source)
            VALUES (?, 'C00000001', ?, ?, ?, 100.00, 'test')
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, subId)
            statement.setString(2, contributorName)
            statement.setString(3, zipCode)
            statement.setString(4, employer)
            statement.executeUpdate()
        }
    }

    /**
     * Inserts only a canonical `contribution` row.
     */
    private fun insertContributionOnly(subId: String) {
        connection.prepareStatement(
            """
            INSERT INTO contribution
                (source, source_record_id, amount, committee_id)
            VALUES ('test', ?, 100.00, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, subId)
            statement.setLong(2, committeeId)
            statement.executeUpdate()
        }
    }
}
