package com.campaignfinances.pipeline.ingestion

import java.sql.Connection

/**
 * Normalizes one ingest run's staging rows into the canonical schema
 * (docs/TDS_PHASE1.md §4): `staging_candidate` → `candidate`,
 * `staging_committee` → `committee`, `staging_linkage` → `candidate_committee`,
 * `staging_contribution` → `contribution`.
 *
 * Every method is idempotent: it upserts on the canonical table's FEC-derived
 * unique key, so re-running the same staging snapshot is a no-op on unchanged
 * rows (docs/TDS_PHASE1.md §4, idempotency). `contribution.donor_id` is
 * deliberately never written here — it starts `NULL` on insert and is left
 * untouched on update, because donor assignment belongs to the dedup job
 * (PR-156); an upsert here must never clobber a donor link dedup already set.
 *
 * Rows whose foreign key can't be resolved yet (e.g. a `ccl` row naming a
 * candidate or committee that was malformed/absent from `cn`/`cm` in the same
 * run) are skipped rather than failing the run, and counted as `bad` in the
 * returned [FileCounts] — consistent with the bulk file parsing stage's
 * tolerate-and-count policy (see [FecBulkAdapter]).
 *
 * None of the `loaded`/`bad` counts below are derived from an `INSERT ...
 * ON DUPLICATE KEY UPDATE` statement's own affected-rows return value — that
 * count is unusable as a row count here. Per MySQL's documented behavior,
 * such a statement reports 1 per newly-inserted row, 1 per matched row whose
 * values are unchanged (Connector/J's default "found rows" reporting), but
 * **2** per matched row whose values actually changed. Earlier versions of
 * [loadCandidates] and [loadContributions] derived `bad` as `total - loaded`
 * using that return value directly; that silently breaks (inflated `loaded`,
 * `bad` going negative) the moment a re-run's staging data differs from what
 * was already loaded — i.e. exactly when FEC republishes a corrected
 * candidate, committee, or contribution record, a routine and expected
 * occurrence, not an edge case. `loaded`/`bad` are always derived from
 * `COUNT(*)` queries instead.
 *
 * @param connection an open connection; not closed here
 */
class CanonicalLoader(private val connection: Connection) {

    /**
     * Upserts `staging_candidate` rows for [ingestRunId] into `candidate`.
     *
     * `candidate.name` and `candidate.office` are `NOT NULL` (migration V2),
     * but `staging_candidate.cand_name`/`cand_office` are nullable — FEC's
     * real bulk files do contain candidates with a blank name or office field
     * (the test fixtures never exercised this). Rows missing either are
     * excluded from the insert and counted as `bad` rather than letting one
     * such row throw a `SQLIntegrityConstraintViolationException` and fail
     * the whole run.
     */
    fun loadCandidates(ingestRunId: Long): FileCounts {
        executeUpdate(
            """
            INSERT INTO candidate (fec_candidate_id, name, office, party, state, district)
            SELECT cand_id, cand_name, cand_office, cand_pty_affiliation, cand_office_st, cand_office_district
            FROM staging_candidate
            WHERE ingest_run_id = ? AND cand_name IS NOT NULL AND cand_office IS NOT NULL
            ON DUPLICATE KEY UPDATE
                name = VALUES(name), office = VALUES(office), party = VALUES(party),
                state = VALUES(state), district = VALUES(district)
            """.trimIndent(),
            ingestRunId,
        )
        val total = scalarCount("SELECT COUNT(*) FROM staging_candidate WHERE ingest_run_id = ?", ingestRunId)
        val bad = scalarCount(
            """
            SELECT COUNT(*) FROM staging_candidate
            WHERE ingest_run_id = ? AND (cand_name IS NULL OR cand_office IS NULL)
            """.trimIndent(),
            ingestRunId,
        )
        return FileCounts(total - bad, bad)
    }

    /**
     * Upserts `staging_committee` rows for [ingestRunId] into `committee`.
     * Every staging row is attempted — there is no FK or NOT-NULL condition
     * to fail here — so `loaded` is simply the staging row count for this
     * run, not the INSERT's own affected-rows return value (see class doc on
     * why that value can't be used as a row count).
     */
    fun loadCommittees(ingestRunId: Long): FileCounts {
        executeUpdate(
            """
            INSERT INTO committee (fec_committee_id, name, type, designation)
            SELECT cmte_id, cmte_nm, cmte_tp, cmte_dsgn
            FROM staging_committee WHERE ingest_run_id = ?
            ON DUPLICATE KEY UPDATE
                name = VALUES(name), type = VALUES(type), designation = VALUES(designation)
            """.trimIndent(),
            ingestRunId,
        )
        val total = scalarCount("SELECT COUNT(*) FROM staging_committee WHERE ingest_run_id = ?", ingestRunId)
        return FileCounts(total, bad = 0)
    }

    /**
     * Upserts `staging_linkage` rows for [ingestRunId] into `candidate_committee`,
     * resolving the surrogate keys via the FEC ids. **All** linkage types are
     * loaded, not just principal (`P`) committees — attribution to principal
     * committees is a ranking-query-time filter (PR-158), not a load-time one,
     * so the full linkage history stays queryable (docs/TDS_PHASE1.md §4).
     *
     * `INSERT IGNORE` rather than `ON DUPLICATE KEY UPDATE`: every column is
     * part of the primary key, so a duplicate means an identical row — there is
     * nothing to update, only a safe no-op to skip.
     */
    fun loadCandidateCommittees(ingestRunId: Long): FileCounts {
        val loaded = executeUpdate(
            """
            INSERT IGNORE INTO candidate_committee (candidate_id, committee_id, linkage_type)
            SELECT c.id, cm.id, sl.cmte_dsgn
            FROM staging_linkage sl
            JOIN candidate c ON c.fec_candidate_id = sl.cand_id
            JOIN committee cm ON cm.fec_committee_id = sl.cmte_id
            WHERE sl.ingest_run_id = ? AND sl.cmte_dsgn IS NOT NULL
            """.trimIndent(),
            ingestRunId,
        )
        val skipped = scalarCount(
            """
            SELECT COUNT(*) FROM staging_linkage sl
            LEFT JOIN candidate c ON c.fec_candidate_id = sl.cand_id
            LEFT JOIN committee cm ON cm.fec_committee_id = sl.cmte_id
            WHERE sl.ingest_run_id = ? AND (c.id IS NULL OR cm.id IS NULL OR sl.cmte_dsgn IS NULL)
            """.trimIndent(),
            ingestRunId,
        )
        return FileCounts(loaded, skipped)
    }

    /**
     * Upserts `staging_contribution` rows for [ingestRunId] into `contribution`,
     * resolving `committee_id` via the FEC id and converting the `MMDDYYYY`
     * staging date to a SQL `DATE` (`NULL` stays `NULL`). `donor_id` is never
     * written here (see class doc).
     *
     * The skip count is a second `LEFT JOIN ... IS NULL` query, same shape as
     * [loadCandidateCommittees] — contributions are by far the largest
     * staging table (tens of millions of rows per cycle, per
     * docs/TDS_PHASE1.md §7), so this does cost a second scan of this run's
     * rows, but see the class doc for why the cheaper affected-rows-based
     * shortcut this method used to take is not just slower, it is wrong.
     */
    fun loadContributions(ingestRunId: Long): FileCounts {
        executeUpdate(
            """
            INSERT INTO contribution (source, source_record_id, amount, contribution_date, committee_id)
            SELECT sc.source, sc.sub_id, sc.transaction_amt,
                   STR_TO_DATE(sc.transaction_dt, '%m%d%Y'), cm.id
            FROM staging_contribution sc
            JOIN committee cm ON cm.fec_committee_id = sc.cmte_id
            WHERE sc.ingest_run_id = ?
            ON DUPLICATE KEY UPDATE
                amount = VALUES(amount),
                contribution_date = VALUES(contribution_date),
                committee_id = VALUES(committee_id)
            """.trimIndent(),
            ingestRunId,
        )
        val total = scalarCount("SELECT COUNT(*) FROM staging_contribution WHERE ingest_run_id = ?", ingestRunId)
        val bad = scalarCount(
            """
            SELECT COUNT(*) FROM staging_contribution sc
            LEFT JOIN committee cm ON cm.fec_committee_id = sc.cmte_id
            WHERE sc.ingest_run_id = ? AND cm.id IS NULL
            """.trimIndent(),
            ingestRunId,
        )
        return FileCounts(total - bad, bad)
    }

    /** Binds [ingestRunId] as the sole parameter and runs the update, returning the row count JDBC reports. */
    private fun executeUpdate(sql: String, ingestRunId: Long): Long {
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, ingestRunId)
            return statement.executeUpdate().toLong()
        }
    }

    /** Binds [ingestRunId] as the sole parameter and returns the single `COUNT(*)`-shaped result of [sql]. */
    private fun scalarCount(sql: String, ingestRunId: Long): Long {
        connection.prepareStatement(sql).use { statement ->
            statement.setLong(1, ingestRunId)
            statement.executeQuery().use { rs ->
                rs.next()
                return rs.getLong(1)
            }
        }
    }
}
