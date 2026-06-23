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
        val loaded = executeUpdate(
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
        return FileCounts(loaded, total - loaded)
    }

    /** Upserts `staging_committee` rows for [ingestRunId] into `committee`. */
    fun loadCommittees(ingestRunId: Long): FileCounts = upsert(
        """
        INSERT INTO committee (fec_committee_id, name, type, designation)
        SELECT cmte_id, cmte_nm, cmte_tp, cmte_dsgn
        FROM staging_committee WHERE ingest_run_id = ?
        ON DUPLICATE KEY UPDATE
            name = VALUES(name), type = VALUES(type), designation = VALUES(designation)
        """.trimIndent(),
        ingestRunId,
    )

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
     * Unlike [loadCandidateCommittees], the skip count here is derived as
     * `total staging rows for this run - loaded` rather than a second `LEFT
     * JOIN ... IS NULL` query: contributions are by far the largest staging
     * table (tens of millions of rows per cycle, per docs/TDS_PHASE1.md §7),
     * so this avoids scanning it twice. That subtraction is only valid because
     * `ON DUPLICATE KEY UPDATE`'s affected-rows count includes *matched* rows
     * even when their values are unchanged (confirmed by
     * `CanonicalLoaderTest`'s idempotency case) — every row this query joins
     * to a committee is counted in `loaded`, on the first run or the tenth.
     * [loadCandidateCommittees] can't use the same trick because `INSERT
     * IGNORE` reports 0 for an already-present row, which would make a
     * fully-resolved re-run look like every row was skipped.
     */
    fun loadContributions(ingestRunId: Long): FileCounts {
        val loaded = executeUpdate(
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
        return FileCounts(loaded, total - loaded)
    }

    /** Runs an upsert with no FK resolution to fail, so `bad` is always 0. */
    private fun upsert(sql: String, ingestRunId: Long): FileCounts =
        FileCounts(executeUpdate(sql, ingestRunId), bad = 0)

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
