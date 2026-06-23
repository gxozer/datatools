package com.campaignfinances.pipeline.ingestion

import com.campaignfinances.pipeline.db.DbConfig
import com.campaignfinances.pipeline.db.Migrator
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Tests [CanonicalLoader] directly against a real MySQL (Testcontainers),
 * bypassing [FecBulkAdapter] so each canonical stage's SQL — upsert,
 * idempotency, attribution, and unresolved-FK skipping — can be exercised in
 * isolation with hand-built staging rows (docs/TDS_PHASE1.md §4, §5.5).
 *
 * End-to-end wiring through a real bulk ingest is covered separately by
 * [FecBulkIngestIntegrationTest].
 */
@Testcontainers
class CanonicalLoaderTest {

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4")
    }

    private lateinit var connection: Connection
    private lateinit var loader: CanonicalLoader

    /**
     * Migrates a fresh connection and wipes every pipeline table before each
     * test. Required because the `@Container` MySQL instance is shared across
     * all test methods in this class (started once, not per test) — without
     * this, a row inserted by one test is still present when the next test
     * runs, silently turning an idempotent upsert into a no-op and breaking
     * that test's row-count assertions.
     */
    @BeforeEach
    fun setUp() {
        val config = DbConfig(mysql.jdbcUrl, mysql.username, mysql.password)
        Migrator(config).migrate()
        connection = DriverManager.getConnection(config.url, config.user, config.password)
        connection.truncateAllPipelineTables()
        loader = CanonicalLoader(connection)
    }

    // --- candidate ---

    @Test
    fun `loadCandidates upserts staging rows into candidate and is idempotent on re-run`() {
        val runId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCandidate(runId, "H6CA34245", "LIEU, TED", "H", "DEM", "CA", "36")

        val first = loader.loadCandidates(runId)
        assertEquals(FileCounts(loaded = 1, bad = 0), first)
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM candidate"))
        assertEquals("LIEU, TED", connection.queryString("SELECT name FROM candidate WHERE fec_candidate_id = 'H6CA34245'"))

        // Re-running the same staging snapshot must not create a second row.
        loader.loadCandidates(runId)
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM candidate"))
    }

    @Test
    fun `loadCandidates reports an unchanged row as loaded, not bad, on re-run`() {
        // Pins an assumption loadCandidates relies on but doesn't enforce:
        // `ON DUPLICATE KEY UPDATE`'s affected-rows count must report 1 for a
        // *matched* row even when no column value actually changed (MySQL
        // Connector/J's default "found rows" reporting, not MySQL's native
        // 0=unchanged/1=inserted/2=updated affected-rows mode). `loadCandidates`
        // derives `bad = total - loaded`, so if a future driver upgrade or a
        // `useAffectedRows=true` JDBC URL parameter ever flips this default,
        // every idempotent re-run would silently start reporting `bad` rows
        // that aren't actually bad — same reasoning as the KDoc on
        // [CanonicalLoader.loadContributions]. If this test ever fails, that's
        // the first place to look, not a bug in the upsert SQL itself.
        val runId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCandidate(runId, "H6CA34245", "LIEU, TED", "H", "DEM", "CA", "36")
        loader.loadCandidates(runId)

        // Re-run with byte-for-byte identical staging data — nothing changed.
        val second = loader.loadCandidates(runId)

        assertEquals(FileCounts(loaded = 1, bad = 0), second)
    }

    @Test
    fun `loadCandidates skips and counts a staging row with a blank name or office instead of failing the run`() {
        val runId = IngestRunRepository.start(connection, "fec-bulk")
        // Real FEC bulk files do contain candidates with a blank CAND_NAME or
        // CAND_OFFICE field; staging stores that as NULL, but candidate.name
        // and candidate.office are NOT NULL (migration V2).
        insertStagingCandidate(runId, "H6AZ08210", name = null, office = "H", party = null, state = "AZ", district = null)
        insertStagingCandidate(runId, "H6CA34245", "LIEU, TED", office = null, party = "DEM", state = "CA", district = "36")
        insertStagingCandidate(runId, "S6TX99999", "DOE, JANE", "S", "REP", "TX", "00")

        val counts = loader.loadCandidates(runId)

        assertEquals(FileCounts(loaded = 1, bad = 2), counts)
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM candidate"))
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM candidate WHERE fec_candidate_id = 'S6TX99999'"))
    }

    @Test
    fun `loadCandidates updates the canonical row when a later, separate run has a corrected name`() {
        // Distinct from the idempotency test above: this uses two genuinely
        // separate ingest_run_ids (the real-world shape — every ingest() call
        // gets a fresh run id), not the same loader call re-invoked with the
        // same run id. Mirrors FEC republishing a corrected candidate name.
        val firstRunId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCandidate(firstRunId, "H6CA34245", "LIEU, T.", "H", "DEM", "CA", "36")
        loader.loadCandidates(firstRunId)
        assertEquals("LIEU, T.", connection.queryString("SELECT name FROM candidate WHERE fec_candidate_id = 'H6CA34245'"))

        val secondRunId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCandidate(secondRunId, "H6CA34245", "LIEU, TED", "H", "DEM", "CA", "36")
        val counts = loader.loadCandidates(secondRunId)

        assertEquals(FileCounts(loaded = 1, bad = 0), counts)
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM candidate"), "must update in place, not duplicate")
        assertEquals("LIEU, TED", connection.queryString("SELECT name FROM candidate WHERE fec_candidate_id = 'H6CA34245'"))
    }

    // --- committee ---

    @Test
    fun `loadCommittees upserts staging rows into committee and is idempotent on re-run`() {
        val runId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCommittee(runId, "C00386987", "TED LIEU FOR CONGRESS", "H", "P")

        val first = loader.loadCommittees(runId)
        assertEquals(FileCounts(loaded = 1, bad = 0), first)
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM committee"))

        loader.loadCommittees(runId)
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM committee"))
    }

    @Test
    fun `loadCommittees updates the canonical row when a later, separate run has a corrected name`() {
        val firstRunId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCommittee(firstRunId, "C00386987", "TED LIEU FOR CONGRSS", "H", "P")
        loader.loadCommittees(firstRunId)
        assertEquals("TED LIEU FOR CONGRSS", connection.queryString("SELECT name FROM committee WHERE fec_committee_id = 'C00386987'"))

        val secondRunId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCommittee(secondRunId, "C00386987", "TED LIEU FOR CONGRESS", "H", "P")
        val counts = loader.loadCommittees(secondRunId)

        assertEquals(FileCounts(loaded = 1, bad = 0), counts)
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM committee"), "must update in place, not duplicate")
        assertEquals("TED LIEU FOR CONGRESS", connection.queryString("SELECT name FROM committee WHERE fec_committee_id = 'C00386987'"))
    }

    // --- candidate_committee (attribution) ---

    @Test
    fun `loadCandidateCommittees loads every linkage type, not only principal P`() {
        val runId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCandidate(runId, "H6CA34245", "LIEU, TED", "H", "DEM", "CA", "36")
        insertStagingCommittee(runId, "C00386987", "TED LIEU FOR CONGRESS", "H", "P")
        insertStagingCommittee(runId, "C00999999", "SOME JOINT FUNDRAISER", "H", "J")
        loader.loadCandidates(runId)
        loader.loadCommittees(runId)

        // Principal committee (P) and a non-principal designation (J, joint
        // fundraiser) both linked to the same candidate.
        insertStagingLinkage(runId, "H6CA34245", "C00386987", "P")
        insertStagingLinkage(runId, "H6CA34245", "C00999999", "J")

        val counts = loader.loadCandidateCommittees(runId)
        assertEquals(FileCounts(loaded = 2, bad = 0), counts)

        // The load itself does not filter to P — that is a ranking-query-time
        // decision (PR-158), so a non-P row must still be present here.
        assertEquals(
            1,
            connection.queryLong("SELECT COUNT(*) FROM candidate_committee WHERE linkage_type <> 'P'"),
            "non-principal linkage rows must still be loaded",
        )
        assertEquals(2, connection.queryLong("SELECT COUNT(*) FROM candidate_committee"))
    }

    @Test
    fun `loadCandidateCommittees skips and counts linkage rows with an unresolved candidate or committee`() {
        val runId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCommittee(runId, "C00386987", "TED LIEU FOR CONGRESS", "H", "P")
        loader.loadCommittees(runId)
        // No candidate H6CA34245 was ever loaded into `candidate`.
        insertStagingLinkage(runId, "H6CA34245", "C00386987", "P")

        val counts = loader.loadCandidateCommittees(runId)
        assertEquals(FileCounts(loaded = 0, bad = 1), counts)
        assertEquals(0, connection.queryLong("SELECT COUNT(*) FROM candidate_committee"))
    }

    @Test
    fun `loadCandidateCommittees skips and counts a linkage row with a blank designation`() {
        // Candidate and committee both resolve, but cmte_dsgn itself is blank
        // — a different failure mode than an unresolved FK, currently counted
        // under the same `bad` bucket (see CanonicalLoader.loadCandidateCommittees
        // KDoc; not planned to be split out, just pinned here).
        val runId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCandidate(runId, "H6CA34245", "LIEU, TED", "H", "DEM", "CA", "36")
        insertStagingCommittee(runId, "C00386987", "TED LIEU FOR CONGRESS", "H", "P")
        loader.loadCandidates(runId)
        loader.loadCommittees(runId)
        insertStagingLinkage(runId, "H6CA34245", "C00386987", designation = null)

        val counts = loader.loadCandidateCommittees(runId)
        assertEquals(FileCounts(loaded = 0, bad = 1), counts)
        assertEquals(0, connection.queryLong("SELECT COUNT(*) FROM candidate_committee"))
    }

    // --- contribution ---

    @Test
    fun `loadContributions resolves committee_id, is idempotent, and never writes donor_id`() {
        val runId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCommittee(runId, "C00386987", "TED LIEU FOR CONGRESS", "H", "P")
        loader.loadCommittees(runId)
        insertStagingContribution(runId, "4041520261234567890", "C00386987", "500.00", "03152026")

        val first = loader.loadContributions(runId)
        assertEquals(FileCounts(loaded = 1, bad = 0), first)
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM contribution"))
        assertNull(connection.queryString("SELECT donor_id FROM contribution WHERE source_record_id = '4041520261234567890'"))
        assertEquals(
            "2026-03-15",
            connection.queryString("SELECT contribution_date FROM contribution WHERE source_record_id = '4041520261234567890'"),
        )

        // Pretend dedup (PR-156) already linked this contribution to a donor.
        insertDonorAndLink("4041520261234567890")

        // Re-running the same staging snapshot must not duplicate the row nor
        // clobber the donor_id dedup already assigned.
        val second = loader.loadContributions(runId)
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM contribution"))
        assertEquals(
            1,
            connection.queryLong(
                "SELECT COUNT(*) FROM contribution WHERE source_record_id = '4041520261234567890' AND donor_id IS NOT NULL",
            ),
            "re-running canonical load must not clobber a donor_id dedup already assigned",
        )
        assertEquals(FileCounts(loaded = 1, bad = 0), second)
    }

    @Test
    fun `loadContributions skips and counts rows whose committee can't be resolved`() {
        val runId = IngestRunRepository.start(connection, "fec-bulk")
        // No committee C00999999 was ever loaded into `committee`.
        insertStagingContribution(runId, "4041520261234567899", "C00999999", "100.00", "03152026")

        val counts = loader.loadContributions(runId)
        assertEquals(FileCounts(loaded = 0, bad = 1), counts)
        assertEquals(0, connection.queryLong("SELECT COUNT(*) FROM contribution"))
    }

    @Test
    fun `loadContributions maps a blank staging date to a null contribution_date`() {
        val runId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCommittee(runId, "C00386987", "TED LIEU FOR CONGRESS", "H", "P")
        loader.loadCommittees(runId)
        insertStagingContribution(runId, "4041520261234567891", "C00386987", "100.00", transactionDt = null)

        loader.loadContributions(runId)

        assertNull(connection.queryString("SELECT contribution_date FROM contribution WHERE source_record_id = '4041520261234567891'"))
    }

    @Test
    fun `loadContributions updates amount and contribution_date when a later, separate run has corrected data`() {
        // Mirrors FEC reissuing a corrected bulk file for the same sub_id —
        // two genuinely separate ingest_run_ids, not the same run re-invoked.
        val committeeRunId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingCommittee(committeeRunId, "C00386987", "TED LIEU FOR CONGRESS", "H", "P")
        loader.loadCommittees(committeeRunId)

        val firstRunId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingContribution(firstRunId, "4041520261234567890", "C00386987", "100.00", "03152026")
        loader.loadContributions(firstRunId)
        assertEquals("100.00", connection.queryString("SELECT amount FROM contribution WHERE source_record_id = '4041520261234567890'"))

        val secondRunId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingContribution(secondRunId, "4041520261234567890", "C00386987", "150.00", "03162026")
        val counts = loader.loadContributions(secondRunId)

        assertEquals(FileCounts(loaded = 1, bad = 0), counts)
        assertEquals(1, connection.queryLong("SELECT COUNT(*) FROM contribution"), "must update in place, not duplicate")
        assertEquals("150.00", connection.queryString("SELECT amount FROM contribution WHERE source_record_id = '4041520261234567890'"))
        assertEquals(
            "2026-03-16",
            connection.queryString("SELECT contribution_date FROM contribution WHERE source_record_id = '4041520261234567890'"),
        )
    }

    // --- donor-match normalization columns (V12) ---

    /**
     * Table-driven: each row is one raw `(contributor_name, zip_code)` pair
     * and the normalized `(normalized_name, zip5)` the generated columns
     * (V12 migration) must compute, per the donor-match normalization rule
     * (docs/TDS_PHASE1.md §5.5) — uppercase, trim, collapse whitespace, and
     * take the first 5 digits of the ZIP.
     */
    @ParameterizedTest(name = "normalizes ''{0}''/''{1}'' to ''{2}''/''{3}''")
    @CsvSource(
        value = [
            "smith, john|900121234|SMITH, JOHN|90012",
            "  Smith,   John  |90012|SMITH, JOHN|90012",
            "O'Brien,  Pat |123456789|O'BRIEN, PAT|12345",
            "Lee|90210|LEE|90210",
        ],
        delimiter = '|',
        ignoreLeadingAndTrailingWhitespace = false,
    )
    fun `donor-match generated columns normalize name and zip5`(
        rawName: String,
        rawZip: String,
        expectedNormalizedName: String,
        expectedZip5: String,
    ) {
        val runId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingContribution(
            runId,
            subId = "4041520260000000001",
            cmteId = "C00386987",
            amount = "1.00",
            transactionDt = "01012026",
            contributorName = rawName,
            zipCode = rawZip,
        )

        assertEquals(
            expectedNormalizedName,
            connection.queryString("SELECT normalized_name FROM staging_contribution WHERE sub_id = '4041520260000000001'"),
        )
        assertEquals(
            expectedZip5,
            connection.queryString("SELECT zip5 FROM staging_contribution WHERE sub_id = '4041520260000000001'"),
        )
    }

    @Test
    fun `donor-match generated columns tolerate a blank contributor_name`() {
        // Real FEC contribution rows can have a blank NAME field too (the
        // parser nulls it, same as CAND_NAME); normalized_name isn't NOT NULL
        // so this must not throw — same bug class as the candidate.name fix.
        val runId = IngestRunRepository.start(connection, "fec-bulk")
        insertStagingContribution(
            runId,
            subId = "4041520260000000002",
            cmteId = "C00386987",
            amount = "1.00",
            transactionDt = "01012026",
            contributorName = null,
        )

        assertNull(connection.queryString("SELECT normalized_name FROM staging_contribution WHERE sub_id = '4041520260000000002'"))
        assertEquals("90012", connection.queryString("SELECT zip5 FROM staging_contribution WHERE sub_id = '4041520260000000002'"))
    }

    // --- test helpers ---

    private fun insertStagingCandidate(
        runId: Long,
        candId: String,
        name: String?,
        office: String?,
        party: String?,
        state: String?,
        district: String?,
    ) {
        connection.prepareStatement(
            """
            INSERT INTO staging_candidate
                (cand_id, cand_name, cand_office, cand_pty_affiliation, cand_office_st, cand_office_district, source, ingest_run_id)
            VALUES (?, ?, ?, ?, ?, ?, 'fec-bulk', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, candId)
            statement.setString(2, name)
            statement.setString(3, office)
            statement.setString(4, party)
            statement.setString(5, state)
            statement.setString(6, district)
            statement.setLong(7, runId)
            statement.executeUpdate()
        }
    }

    private fun insertStagingCommittee(runId: Long, cmteId: String, name: String, type: String, designation: String) {
        connection.prepareStatement(
            "INSERT INTO staging_committee (cmte_id, cmte_nm, cmte_tp, cmte_dsgn, source, ingest_run_id) VALUES (?, ?, ?, ?, 'fec-bulk', ?)",
        ).use { statement ->
            statement.setString(1, cmteId)
            statement.setString(2, name)
            statement.setString(3, type)
            statement.setString(4, designation)
            statement.setLong(5, runId)
            statement.executeUpdate()
        }
    }

    private fun insertStagingLinkage(runId: Long, candId: String, cmteId: String, designation: String?) {
        connection.prepareStatement(
            "INSERT INTO staging_linkage (cand_id, cmte_id, cmte_dsgn, source, ingest_run_id) VALUES (?, ?, ?, 'fec-bulk', ?)",
        ).use { statement ->
            statement.setString(1, candId)
            statement.setString(2, cmteId)
            statement.setString(3, designation)
            statement.setLong(4, runId)
            statement.executeUpdate()
        }
    }

    private fun insertStagingContribution(
        runId: Long,
        subId: String,
        cmteId: String,
        amount: String,
        transactionDt: String?,
        contributorName: String? = "DOE, JANE",
        zipCode: String = "900121234",
    ) {
        connection.prepareStatement(
            """
            INSERT INTO staging_contribution
                (sub_id, cmte_id, contributor_name, city, state, zip_code, employer, occupation,
                 transaction_dt, transaction_amt, source, ingest_run_id)
            VALUES (?, ?, ?, 'ANYTOWN', 'CA', ?, 'ANY EMPLOYER', 'ANY OCCUPATION', ?, ?, 'fec-bulk', ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, subId)
            statement.setString(2, cmteId)
            statement.setString(3, contributorName)
            statement.setString(4, zipCode)
            statement.setString(5, transactionDt)
            statement.setBigDecimal(6, java.math.BigDecimal(amount))
            statement.setLong(7, runId)
            statement.executeUpdate()
        }
    }

    /** Simulates the dedup job (PR-156) having already linked a contribution to a donor. */
    private fun insertDonorAndLink(contributionSourceRecordId: String) {
        val donorId = connection.prepareStatement(
            "INSERT INTO donor (canonical_name, zip5) VALUES ('DOE, JANE', '90012')",
            java.sql.Statement.RETURN_GENERATED_KEYS,
        ).use { statement ->
            statement.executeUpdate()
            statement.generatedKeys.use { keys -> keys.next(); keys.getLong(1) }
        }
        connection.prepareStatement("UPDATE contribution SET donor_id = ? WHERE source_record_id = ?").use { statement ->
            statement.setLong(1, donorId)
            statement.setString(2, contributionSourceRecordId)
            statement.executeUpdate()
        }
    }
}
