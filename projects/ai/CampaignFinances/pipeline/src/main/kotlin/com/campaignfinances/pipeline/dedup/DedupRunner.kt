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
 * A staging row joined to its canonical contribution id, ready for normalization.
 */
private data class StagingRow(
    val contributionId: Long,
    val contributorName: String?,
    val zipCode: String?,
    val employer: String?,
)

/**
 * The normalized key used to group contributions into a single donor (PR-156,
 * TDS §5 step 2).
 *
 * Blank employer is represented as empty string, forming its own bucket
 * separate from any named employer — the conservative interpretation of
 * "employer matches or either is blank" that avoids transitively merging
 * different named employers through shared blank-employer rows.
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
 * Reads all rows from `staging_contribution`, normalizes name/employer/zip via
 * [DonorNormalizer], groups by the conservative match key
 * `(last_name, first_name, zip5, employer)`, and writes one `donor` row per
 * group with `donor_link` audit rows linking each canonical `contribution` to
 * its donor. Also stamps `contribution.donor_id`.
 *
 * **Re-runnable:** always clears `donor`, `donor_link`, and
 * `contribution.donor_id` before rebuilding, so rule changes can be applied
 * with a plain `dedup` re-run without re-ingesting.
 *
 * **Blank employer strategy:** contributions with no employer form their own
 * donor group per name+zip5, separate from any named-employer group. This
 * avoids the transitivity trap where blank-employer rows would merge two
 * different named employers into one donor.
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
     * Runs the full dedup job: clear → load → normalize → group → write.
     *
     * @return summary counts for the completed run
     */
    fun run(): DedupSummary {
        dbConfig.openConnection().use { connection ->
            out.appendLine("dedup: clearing previous results")
            clearExistingDedup(connection)

            out.appendLine("dedup: loading staging rows")
            val stagingRows = loadStagingRows(connection)
            out.appendLine("dedup: ${stagingRows.size} staging rows loaded")

            val groups = groupByDonorKey(stagingRows)
            val totalGrouped = groups.values.sumOf { it.size }
            val skipped = stagingRows.size.toLong() - totalGrouped
            out.appendLine("dedup: ${groups.size} donor groups, $skipped rows skipped (blank name or zip)")
            logger.debug { "dedup: ${groups.size} donor groups from ${stagingRows.size} staging rows, $skipped skipped" }

            var donorsCreated = 0L
            var linksCreated = 0L
            for ((key, rows) in groups) {
                val donorId = insertDonor(connection, key)
                val matchRule = if (key.employer.isEmpty()) MATCH_RULE_NO_EMPLOYER else MATCH_RULE_EMPLOYER
                val contributionIds = rows.map { it.contributionId }
                insertDonorLinks(connection, donorId, contributionIds, matchRule)
                updateContributionDonorIds(connection, donorId, contributionIds)
                donorsCreated++
                linksCreated += rows.size
            }

            out.appendLine("dedup: $donorsCreated donors created, $linksCreated links written, $skipped skipped")
            return DedupSummary(donorsCreated, linksCreated, skipped)
        }
    }

    /**
     * Removes all previous dedup results in dependency order so foreign key
     * constraints are satisfied:
     * 1. `donor_link` (references both `donor` and `contribution`)
     * 2. `contribution.donor_id` (FK to `donor`)
     * 3. `donor`
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
     * Loads all staging rows that have a matching canonical contribution record.
     * The INNER JOIN excludes orphaned staging rows (e.g., from failed ingest
     * runs whose staging data was not cleaned up).
     *
     * @param connection an open connection; not closed by this method
     * @return all joined rows, unordered
     */
    private fun loadStagingRows(connection: Connection): List<StagingRow> {
        val sql = """
            SELECT c.id, s.contributor_name, s.zip_code, s.employer
            FROM staging_contribution s
            JOIN contribution c ON c.source = s.source AND c.source_record_id = s.sub_id
        """.trimIndent()
        val rows = mutableListOf<StagingRow>()
        connection.prepareStatement(sql).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    rows.add(
                        StagingRow(
                            contributionId = rs.getLong(1),
                            contributorName = rs.getString(2),
                            zipCode = rs.getString(3),
                            employer = rs.getString(4),
                        ),
                    )
                }
            }
        }
        return rows
    }

    /**
     * Applies [DonorNormalizer] to each row and groups by [DonorKey]. Rows
     * where name or zip5 cannot be normalized are silently excluded — those
     * contributions retain `donor_id = NULL`.
     *
     * @param rows the raw staging rows from [loadStagingRows]
     * @return a map of donor key to the rows belonging to that group
     */
    private fun groupByDonorKey(rows: List<StagingRow>): Map<DonorKey, List<StagingRow>> {
        val groups = mutableMapOf<DonorKey, MutableList<StagingRow>>()
        for (row in rows) {
            val name = DonorNormalizer.parseName(row.contributorName) ?: continue
            val zip5 = DonorNormalizer.normalizeZip(row.zipCode) ?: continue
            val employer = DonorNormalizer.normalizeEmployer(row.employer) ?: ""
            val key = DonorKey(name.lastName, name.firstName, zip5, employer)
            groups.getOrPut(key) { mutableListOf() }.add(row)
        }
        return groups
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
