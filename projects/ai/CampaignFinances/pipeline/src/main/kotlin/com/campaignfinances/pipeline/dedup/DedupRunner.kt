package com.campaignfinances.pipeline.dedup

import com.campaignfinances.pipeline.db.DbConfig
import mu.KotlinLogging
import java.sql.Connection
import java.sql.ResultSet
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
 * Uses two streaming passes over `staging_contribution JOIN contribution` to
 * avoid holding all contribution IDs in JVM heap (PR-207):
 *
 * **Pass 1** — streams every row, inserts one `donor` row the first time a
 * new normalized `(last, first, zip5, employer)` key is seen, and builds a
 * `DonorKey → donor.id` map (O(D) where D = distinct donors). Donor inserts
 * are batched [DONOR_BATCH_SIZE] at a time (PR-208) to eliminate the per-row
 * prepare+execute overhead that caused multi-hour runtimes on large datasets.
 * Progress is logged every [PROGRESS_LOG_INTERVAL] rows.
 *
 * **Pass 2** — streams again, looks up `donor.id` by key, and batch-writes
 * `donor_link` + `contribution.donor_id` as rows arrive.
 *
 * Each pass uses a dedicated read-only connection so the streaming `ResultSet`
 * is isolated from the transactional write connection — this prevents the
 * "streaming result set still active" error that occurred when OOM aborted the
 * read and left the connection in a bad state.
 *
 * **Re-runnable:** always clears `donor`, `donor_link`, and
 * `contribution.donor_id` before rebuilding — the entire clear+rebuild runs in
 * a single JDBC transaction so a failure rolls back to the pre-run state.
 *
 * **Blank employer strategy:** contributions with no employer form their own
 * donor group per name+zip5, separate from any named-employer group. This
 * avoids the transitivity trap where blank-employer rows would merge two
 * different named employers into one donor.
 *
 * **Duplicate staging rows:** `staging_contribution` accumulates rows across
 * API ingest runs (no UNIQUE constraint on source+sub_id). Both passes sort by
 * `contribution.id` and skip consecutive duplicates, so only the first staging
 * row per canonical contribution is used (PR-202).
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

        /**
         * Number of `donor_link` + `contribution` update rows buffered before
         * flushing to the write connection during pass 2. Large enough to amortize
         * round-trip overhead; small enough to keep retry scope narrow on failure.
         */
        private const val LINK_BATCH_SIZE = 1_000

        /**
         * Number of `donor` rows buffered before flushing to the write connection
         * during pass 1. Matches [LINK_BATCH_SIZE] so both passes have the same
         * amortization profile (PR-208).
         */
        private const val DONOR_BATCH_SIZE = 1_000

        /**
         * Number of contributions processed between progress log lines during
         * pass 1. At 10 000 rows per line a 10 M-row dataset produces 1 000 lines.
         */
        private const val PROGRESS_LOG_INTERVAL = 10_000L

        private val STAGING_QUERY = """
            SELECT c.id, s.contributor_name, s.zip_code, s.employer
            FROM staging_contribution s
            JOIN contribution c ON c.source = s.source AND c.source_record_id = s.sub_id
            ORDER BY c.id
        """.trimIndent()
    }

    /**
     * Runs the full dedup job: clear → pass 1 (stream + insert donors) →
     * pass 2 (stream + write links), all wrapped in a single transaction on
     * the write connection.
     *
     * On any exception the transaction is rolled back, leaving the DB in the
     * same state it was before the run started.
     *
     * @return summary counts for the completed run
     */
    fun run(): DedupSummary {
        val writeConnection = dbConfig.openConnection()
        writeConnection.autoCommit = false
        val shutdownHook = Thread { closeConnectionQuietly(writeConnection) }
        Runtime.getRuntime().addShutdownHook(shutdownHook)
        try {
            out.appendLine("dedup: clearing previous results")
            clearExistingDedup(writeConnection)

            out.appendLine("dedup: pass 1 — streaming staging rows and inserting donor records")
            val (keyToDonorId, skipped) = streamDistinctDonors(writeConnection)
            out.appendLine("dedup: ${keyToDonorId.size} distinct donors, $skipped rows skipped (blank name or zip)")
            logger.debug { "dedup pass 1 complete: ${keyToDonorId.size} donors, $skipped skipped" }

            out.appendLine("dedup: pass 2 — streaming staging rows and writing donor links")
            val linksCreated = streamAndWriteLinks(writeConnection, keyToDonorId)

            writeConnection.commit()
            val donorsCreated = keyToDonorId.size.toLong()
            out.appendLine("dedup: $donorsCreated donors created, $linksCreated links written, $skipped skipped")
            return DedupSummary(donorsCreated, linksCreated, skipped)
        } catch (e: Exception) {
            writeConnection.rollback()
            throw e
        } finally {
            writeConnection.autoCommit = true
            writeConnection.close()
            Runtime.getRuntime().removeShutdownHook(shutdownHook)
        }
    }

    /**
     * Closes [connection] and rolls back any open transaction, swallowing all
     * exceptions. Invoked from the JVM shutdown hook so the write connection is
     * not left open — and holding locks — when the process is terminated mid-run
     * (e.g. IntelliJ Stop button / SIGTERM).
     *
     * @param connection the write connection opened in [run]
     */
    private fun closeConnectionQuietly(connection: Connection) {
        try {
            if (!connection.isClosed) {
                connection.rollback()
                connection.close()
            }
        } catch (_: Exception) {
            // best-effort on shutdown — no useful action available here
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
     * @param connection an open write connection; not closed by this method
     */
    private fun clearExistingDedup(connection: Connection) {
        val statement = connection.createStatement()
        try {
            statement.execute("DELETE FROM donor_link")
            statement.execute("UPDATE contribution SET donor_id = NULL")
            statement.execute("DELETE FROM donor")
        } finally {
            statement.close()
        }
    }

    /**
     * Pass 1: counts total contributions for progress display, then streams
     * every staging row and inserts one `donor` row the first time each
     * normalized [DonorKey] is encountered.
     *
     * Uses a dedicated read-only connection for streaming so the write
     * connection (which holds the open transaction) is never blocked by an
     * active streaming `ResultSet` (PR-207).
     *
     * Rows are ordered by `contribution.id`; consecutive rows with the same id
     * are staging duplicates and all but the first are skipped (PR-202).
     *
     * The INSERT statement for `donor` rows is prepared once here and reused
     * across all [DONOR_BATCH_SIZE]-row batches to eliminate per-row prepare
     * overhead (PR-208).
     *
     * @param writeConnection the transactional connection on which `donor` rows
     *   are inserted; not closed by this method
     * @return pair of (DonorKey → donor.id map, count of skipped contributions)
     */
    private fun streamDistinctDonors(writeConnection: Connection): Pair<Map<DonorKey, Long>, Long> {
        val readConnection = dbConfig.openStreamingConnection()
        val insertStatement = writeConnection.prepareStatement(
            "INSERT INTO donor (canonical_name, employer, zip5) VALUES (?, ?, ?)",
            Statement.RETURN_GENERATED_KEYS,
        )
        try {
            val totalRows = countContributions(readConnection)
            out.appendLine("dedup pass 1: $totalRows total contributions")
            logger.debug { "dedup pass 1: totalRows=$totalRows" }

            val readStatement = readConnection.prepareStatement(
                STAGING_QUERY, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY,
            )
            readStatement.fetchSize = Int.MIN_VALUE // activates MySQL row-at-a-time streaming
            val rs = readStatement.executeQuery()

            try {
                return drainDonorRows(rs, insertStatement, totalRows)
            } finally {
                rs.close()
                readStatement.close()
            }
        } finally {
            readConnection.close()
            insertStatement.close()
        }
    }

    /**
     * Returns the count of rows in `contribution`, used as the denominator for
     * pass 1 progress percentage. This is a fast primary-key scan; the result
     * is used for display only and does not need to exactly match the streaming
     * query's deduplicated row count.
     *
     * @param connection an open read connection; not closed by this method
     * @return number of rows in `contribution`
     */
    private fun countContributions(connection: Connection): Long {
        val statement = connection.createStatement()
        try {
            val rs = statement.executeQuery("SELECT COUNT(*) FROM contribution")
            try {
                rs.next()
                return rs.getLong(1)
            } finally {
                rs.close()
            }
        } finally {
            statement.close()
        }
    }

    /**
     * Reads every row from [rs], inserts `donor` rows in batches of
     * [DONOR_BATCH_SIZE] via [insertStatement], and returns the complete
     * key→id map alongside the count of skipped contributions (PR-208).
     *
     * Progress is logged to [out] and [logger] every [PROGRESS_LOG_INTERVAL]
     * rows, showing record number, total, percentage, donors found, and skipped.
     *
     * Separated from [streamDistinctDonors] to keep resource setup and row
     * processing in distinct methods.
     *
     * @param rs open streaming `ResultSet` from the staging query
     * @param insertStatement prepared `INSERT INTO donor` statement, reused across all batches
     * @param totalRows total contribution count used as progress percentage denominator
     * @return pair of (DonorKey → donor.id map, skipped-row count)
     */
    private fun drainDonorRows(
        rs: ResultSet,
        insertStatement: java.sql.PreparedStatement,
        totalRows: Long,
    ): Pair<Map<DonorKey, Long>, Long> {
        val keyToDonorId = mutableMapOf<DonorKey, Long>()
        val pendingKeys = mutableListOf<DonorKey>()
        // Tracks keys queued in the current batch but not yet flushed into keyToDonorId,
        // so that two contributions with the same key within the same batch are not both inserted.
        val pendingKeySet = mutableSetOf<DonorKey>()
        var skipped = 0L
        var rowsProcessed = 0L
        var previousId = Long.MIN_VALUE
        var pendingBatch = 0

        while (rs.next()) {
            val contributionId = rs.getLong(1)
            // ORDER BY c.id puts all staging rows for the same contribution
            // consecutively; skip all but the first (PR-202).
            if (contributionId == previousId) continue
            previousId = contributionId
            rowsProcessed++

            if (rowsProcessed % PROGRESS_LOG_INTERVAL == 0L) {
                val pct = if (totalRows > 0) rowsProcessed * 100 / totalRows else 0
                logger.debug { "dedup pass 1: record $rowsProcessed / $totalRows ($pct%), ${keyToDonorId.size} donors, $skipped skipped" }
            }

            val name = DonorNormalizer.parseName(rs.getString(2))
            val zip5 = DonorNormalizer.normalizeZip(rs.getString(3))

            if (name == null || zip5 == null) {
                skipped++
                continue
            }

            val employer = DonorNormalizer.normalizeEmployer(rs.getString(4)) ?: ""
            val key = DonorKey(name.lastName, name.firstName, zip5, employer)

            if (!keyToDonorId.containsKey(key) && !pendingKeySet.contains(key)) {
                val canonicalName = if (key.firstName.isEmpty()) key.lastName else "${key.lastName}, ${key.firstName}"
                insertStatement.setString(1, canonicalName)
                insertStatement.setString(2, if (key.employer.isEmpty()) null else key.employer)
                insertStatement.setString(3, key.zip5)
                insertStatement.addBatch()
                pendingKeys.add(key)
                pendingKeySet.add(key)
                pendingBatch++

                if (pendingBatch >= DONOR_BATCH_SIZE) {
                    flushDonorBatch(insertStatement, pendingKeys, keyToDonorId)
                    pendingKeys.clear()
                    pendingKeySet.clear()
                    pendingBatch = 0
                }
            }
        }

        if (pendingBatch > 0) {
            flushDonorBatch(insertStatement, pendingKeys, keyToDonorId)
        }

        return Pair(keyToDonorId, skipped)
    }

    /**
     * Executes the buffered `donor` INSERT batch and maps each generated id back
     * to its [DonorKey] in [keyToDonorId].
     *
     * MySQL's Connector/J returns generated keys in insertion order after
     * [executeBatch], so zipping [pendingKeys] with the key ResultSet is correct.
     *
     * @param insertStatement the prepared INSERT statement with a pending batch
     * @param pendingKeys ordered list of [DonorKey] values in the current batch;
     *   must match the order rows were added via `addBatch`
     * @param keyToDonorId accumulator updated with the newly assigned `donor.id` values
     */
    private fun flushDonorBatch(
        insertStatement: java.sql.PreparedStatement,
        pendingKeys: List<DonorKey>,
        keyToDonorId: MutableMap<DonorKey, Long>,
    ) {
        insertStatement.executeBatch()
        val generatedKeys = insertStatement.generatedKeys
        try {
            var index = 0
            while (generatedKeys.next()) {
                keyToDonorId[pendingKeys[index]] = generatedKeys.getLong(1)
                index++
            }
            check(index == pendingKeys.size) { "generated key count $index != batch size ${pendingKeys.size}" }
        } finally {
            generatedKeys.close()
        }
    }

    /**
     * Pass 2: streams every staging row, looks up the `donor.id` by normalized
     * key, and batch-writes `donor_link` rows and `contribution.donor_id`
     * updates to [writeConnection].
     *
     * Uses a second dedicated read-only connection so the streaming `ResultSet`
     * does not block the write connection (PR-207).
     *
     * Rows with no entry in [keyToDonorId] (blank name or zip) are silently
     * skipped — their `contribution.donor_id` remains NULL, consistent with
     * pass 1.
     *
     * Batches are flushed every [LINK_BATCH_SIZE] rows to bound memory use and
     * keep the transaction size manageable.
     *
     * @param writeConnection the transactional connection for `donor_link` inserts
     *   and `contribution` updates; not closed by this method
     * @param keyToDonorId map produced by [streamDistinctDonors]
     * @return count of `donor_link` rows inserted
     */
    private fun streamAndWriteLinks(writeConnection: Connection, keyToDonorId: Map<DonorKey, Long>): Long {
        val linkStatement = writeConnection.prepareStatement(
            "INSERT INTO donor_link (donor_id, contribution_id, match_rule) VALUES (?, ?, ?)",
        )
        val updateStatement = writeConnection.prepareStatement(
            "UPDATE contribution SET donor_id = ? WHERE id = ?",
        )
        val readConnection = dbConfig.openStreamingConnection()
        val readStatement = readConnection.prepareStatement(
            STAGING_QUERY, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY,
        )
        readStatement.fetchSize = Int.MIN_VALUE // activates MySQL row-at-a-time streaming
        val rs = readStatement.executeQuery()

        try {
            return drainLinkRows(rs, keyToDonorId, linkStatement, updateStatement)
        } finally {
            rs.close()
            readStatement.close()
            readConnection.close()
            linkStatement.close()
            updateStatement.close()
        }
    }

    /**
     * Reads every row from [rs] and batch-writes one `donor_link` insert and
     * one `contribution.donor_id` update per unique contribution.
     *
     * Separated from [streamAndWriteLinks] to keep resource setup and row
     * processing in distinct methods.
     *
     * @param rs open streaming `ResultSet` from the staging query
     * @param keyToDonorId map of normalized key → donor.id
     * @param linkStatement open `PreparedStatement` for `donor_link` inserts
     * @param updateStatement open `PreparedStatement` for `contribution` updates
     * @return count of rows added to [linkStatement] batch (= `donor_link` rows written)
     */
    private fun drainLinkRows(
        rs: ResultSet,
        keyToDonorId: Map<DonorKey, Long>,
        linkStatement: java.sql.PreparedStatement,
        updateStatement: java.sql.PreparedStatement,
    ): Long {
        var linksCreated = 0L
        var previousId = Long.MIN_VALUE
        var pendingBatch = 0

        while (rs.next()) {
            val contributionId = rs.getLong(1)
            if (contributionId == previousId) continue
            previousId = contributionId

            val name = DonorNormalizer.parseName(rs.getString(2))
            val zip5 = DonorNormalizer.normalizeZip(rs.getString(3))
            if (name == null || zip5 == null) continue

            val employer = DonorNormalizer.normalizeEmployer(rs.getString(4)) ?: ""
            val key = DonorKey(name.lastName, name.firstName, zip5, employer)
            val donorId = keyToDonorId[key] ?: continue

            val matchRule = if (key.employer.isEmpty()) MATCH_RULE_NO_EMPLOYER else MATCH_RULE_EMPLOYER

            linkStatement.setLong(1, donorId)
            linkStatement.setLong(2, contributionId)
            linkStatement.setString(3, matchRule)
            linkStatement.addBatch()

            updateStatement.setLong(1, donorId)
            updateStatement.setLong(2, contributionId)
            updateStatement.addBatch()

            pendingBatch++
            linksCreated++

            if (pendingBatch >= LINK_BATCH_SIZE) {
                linkStatement.executeBatch()
                updateStatement.executeBatch()
                pendingBatch = 0
            }
        }

        if (pendingBatch > 0) {
            linkStatement.executeBatch()
            updateStatement.executeBatch()
        }

        return linksCreated
    }
}
