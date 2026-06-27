package com.campaignfinances.pipeline.dedup

import com.campaignfinances.pipeline.db.DbConfig
import mu.KotlinLogging
import java.sql.Connection
import java.sql.Statement

private val logger = KotlinLogging.logger {}

/**
 * Counts from one dedup run.
 *
 * @property donorsCreated number of [donor] rows inserted
 * @property linksCreated number of [donor_link] rows inserted
 * @property skipped number of staging rows excluded because name or zip5 could
 *   not be normalized (those contributions retain `donor_id = NULL`)
 */
data class DedupSummary(
    val donorsCreated: Long,
    val linksCreated: Long,
    val skipped: Long,
)

/**
 * The normalized key used to group contributions into a single donor (PR-156,
 * TDS §5 step 2).
 *
 * Blank employer is represented as empty string, forming its own bucket
 * separate from any named employer — the conservative interpretation that avoids
 * transitively merging different named employers through shared blank-employer rows.
 */
private data class DonorKey(
    val lastName: String,
    val firstName: String,
    val zip5: String,
    val employer: String,
)

/**
 * Donor de-duplication runner (PR-156, docs/TDS_PHASE1.md §5).
 *
 * Reads all rows from `staging_contribution` (joined to `contribution`) in a
 * single streaming pass, normalizes name/employer/zip via [DonorNormalizer],
 * groups by `(last_name, first_name, zip5, employer)`, and writes one `donor`
 * row per group with `donor_link` audit rows and a `contribution.donor_id`
 * stamp.
 *
 * **Re-runnable:** always clears `donor`, `donor_link`, and
 * `contribution.donor_id` before rebuilding — the entire clear+rebuild runs in
 * a single JDBC transaction (PR-201) so a failure rolls back to the pre-run
 * state rather than leaving the DB empty.
 *
 * **Blank employer strategy:** contributions with no employer form their own
 * donor group per name+zip5, separate from any named-employer group. This
 * avoids the transitivity trap where blank-employer rows would merge two
 * different named employers into one donor.
 *
 * **Duplicate staging rows:** `staging_contribution` accumulates rows across
 * API ingest runs (no UNIQUE constraint on source+sub_id). The streaming pass
 * deduplicates by `contribution.id` — only the first staging row seen per
 * canonical contribution is used (PR-202).
 *
 * **Skipped rows:** contributions with a null/blank contributor name, or whose
 * zip cannot produce a 5-digit zip5, are excluded — `donor_id` stays NULL.
 *
 * @param dbConfig connection settings
 * @param out where progress lines are printed
 */
class DedupRunner(
    private val dbConfig: DbConfig,
    private val out: Appendable = System.out,
) {
    companion object {
        private const val MATCH_RULE_EMPLOYER = "name+zip5+employer"
        private const val MATCH_RULE_NO_EMPLOYER = "name+zip5 (employer blank)"
    }

    /**
     * Runs the full dedup job: clear → stream+group → write, wrapped in a
     * single transaction.
     *
     * On any exception the transaction is rolled back, leaving the DB in the
     * same state it was before the run started.
     *
     * @return summary counts for the completed run
     */
    fun run(): DedupSummary {
        dbConfig.openConnection().use { connection ->
            connection.autoCommit = false
            try {
                out.appendLine("dedup: clearing previous results")
                clearExistingDedup(connection)

                out.appendLine("dedup: loading and grouping staging rows")
                val (groups, skipped) = streamIntoGroups(connection)
                out.appendLine("dedup: ${groups.size} donor groups, $skipped rows skipped (blank name or zip)")
                logger.debug { "dedup: ${groups.size} donor groups, $skipped skipped" }

                var donorsCreated = 0L
                var linksCreated = 0L
                for ((key, contributionIds) in groups) {
                    val donorId = insertDonor(connection, key)
                    val matchRule = if (key.employer.isEmpty()) MATCH_RULE_NO_EMPLOYER else MATCH_RULE_EMPLOYER
                    insertDonorLinks(connection, donorId, contributionIds, matchRule)
                    updateContributionDonorIds(connection, donorId, contributionIds)
                    donorsCreated++
                    linksCreated += contributionIds.size
                }

                connection.commit()
                out.appendLine("dedup: $donorsCreated donors created, $linksCreated links written, $skipped skipped")
                return DedupSummary(donorsCreated, linksCreated, skipped)
            } catch (e: Exception) {
                connection.rollback()
                throw e
            } finally {
                connection.autoCommit = true
            }
        }
    }

    /**
     * Removes all previous dedup results in dependency order so foreign key
     * constraints are satisfied:
     * 1. `donor_link` (references both `donor` and `contribution`)
     * 2. `contribution.donor_id` (FK to `donor`)
     * 3. `donor`
     *
     * Runs inside the transaction started by [run]; rolled back on failure.
     *
     * @param connection an open connection; not closed by this method
     */
    private fun clearExistingDedup(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.execute("DELETE FROM donor_link")
            statement.execute("UPDATE contribution SET donor_id = NULL")
            statement.execute("DELETE FROM donor")
        }
    }

    /**
     * Streams the staging+contribution join result and builds the donor groups
     * map in a single pass, without materializing the full row list in memory
     * (PR-203: avoids OOM on 50–80M row FEC dataset).
     *
     * Deduplicates by `contribution.id` — if `staging_contribution` has
     * multiple rows for the same `(source, sub_id)` (accumulated from repeated
     * API ingest runs), only the first one encountered per `contribution.id`
     * contributes to the grouping (PR-202).
     *
     * Rows where name or zip5 cannot be normalized are silently excluded;
     * their count is returned as the second element of the pair.
     *
     * @param connection an open connection; not closed by this method
     * @return pair of (donor-key → contribution-id list, skipped-row count)
     */
    private fun streamIntoGroups(
        connection: Connection,
    ): Pair<Map<DonorKey, MutableList<Long>>, Long> {
        val sql = """
            SELECT c.id, s.contributor_name, s.zip_code, s.employer
            FROM staging_contribution s
            JOIN contribution c ON c.source = s.source AND c.source_record_id = s.sub_id
        """.trimIndent()

        val groups = mutableMapOf<DonorKey, MutableList<Long>>()
        val seenIds = mutableSetOf<Long>()
        var skipped = 0L

        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    val contributionId = rs.getLong(1)
                    if (!seenIds.add(contributionId)) continue

                    val name = DonorNormalizer.parseName(rs.getString(2))
                    val zip5 = DonorNormalizer.normalizeZip(rs.getString(3))

                    if (name == null || zip5 == null) {
                        skipped++
                        continue
                    }

                    val employer = DonorNormalizer.normalizeEmployer(rs.getString(4)) ?: ""
                    val key = DonorKey(name.lastName, name.firstName, zip5, employer)

                    if (!groups.containsKey(key)) groups[key] = mutableListOf()
                    groups[key]!!.add(contributionId)
                }
            }
        }
        return Pair(groups, skipped)
    }

    /**
     * Inserts one `donor` row for [key] and returns its auto-increment id.
     * The canonical_name is stored as `"LAST, FIRST"` (or just `"LAST"` when
     * the first name is empty).
     *
     * @param connection an open connection; not closed by this method
     * @param key the normalized donor key for this group
     * @return the generated donor.id
     */
    private fun insertDonor(connection: Connection, key: DonorKey): Long {
        val canonicalName = if (key.firstName.isEmpty()) key.lastName else "${key.lastName}, ${key.firstName}"
        connection.prepareStatement(
            "INSERT INTO donor (canonical_name, employer, zip5) VALUES (?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS,
        ).use { statement ->
            statement.setString(1, canonicalName)
            statement.setString(2, if (key.employer.isEmpty()) null else key.employer)
            statement.setString(3, key.zip5)
            statement.executeUpdate()
            statement.generatedKeys.use { keys ->
                check(keys.next()) { "no generated key for donor" }
                return keys.getLong(1)
            }
        }
    }

    /**
     * Batch-inserts `donor_link` rows for all [contributionIds] in a single
     * prepared statement, recording which [matchRule] caused the grouping.
     *
     * @param connection an open connection; not closed by this method
     * @param donorId the donor these contributions belong to
     * @param contributionIds the canonical contribution.id values in this group
     * @param matchRule label for the rule that fired (stored for audit)
     */
    private fun insertDonorLinks(
        connection: Connection,
        donorId: Long,
        contributionIds: List<Long>,
        matchRule: String,
    ) {
        connection.prepareStatement(
            "INSERT INTO donor_link (donor_id, contribution_id, match_rule) VALUES (?, ?, ?)",
        ).use { statement ->
            for (contributionId in contributionIds) {
                statement.setLong(1, donorId)
                statement.setLong(2, contributionId)
                statement.setString(3, matchRule)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }

    /**
     * Batch-updates `contribution.donor_id` for all [contributionIds].
     *
     * @param connection an open connection; not closed by this method
     * @param donorId the donor id to stamp onto each contribution
     * @param contributionIds the contribution rows to update
     */
    private fun updateContributionDonorIds(
        connection: Connection,
        donorId: Long,
        contributionIds: List<Long>,
    ) {
        connection.prepareStatement(
            "UPDATE contribution SET donor_id = ? WHERE id = ?",
        ).use { statement ->
            for (contributionId in contributionIds) {
                statement.setLong(1, donorId)
                statement.setLong(2, contributionId)
                statement.addBatch()
            }
            statement.executeBatch()
        }
    }
}
