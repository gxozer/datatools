package com.campaignfinances.pipeline.ingestion

/**
 * The four FEC bulk files Phase 1 ingests, with their staging targets.
 *
 * Each cycle, the FEC publishes these as pipe-delimited text inside zip files at
 * `https://www.fec.gov/files/bulk-downloads/<cycle>/<key><yy>.zip`. Field
 * layouts are documented at
 * https://www.fec.gov/campaign-finance-data/all-candidates-file-description/
 * (and sibling pages per file type).
 *
 * Adding a new source file means adding an entry here plus a branch in
 * [FecBulkParser] — the loader and adapter code is generic over this enum.
 *
 * @property key the FEC short name, also used as the key in `ingest_run.row_counts`
 * @property txtName the extracted text file's name inside the zip / local dir
 * @property expectedFields exact pipe-separated field count; lines with any
 *   other count are rejected as malformed
 * @property stagingTable the staging table rows are bulk-loaded into
 * @property stagingColumns the staging columns, in the exact order
 *   [FecBulkParser] emits values
 */
enum class FecBulkFileType(
    val key: String,
    val txtName: String,
    val expectedFields: Int,
    val stagingTable: String,
    val stagingColumns: List<String>,
) {
    /** Candidate master (`cn`): one row per registered candidate. */
    CANDIDATE(
        "cn", "cn.txt", 15, "staging_candidate",
        listOf("cand_id", "cand_name", "cand_office", "cand_pty_affiliation", "cand_office_st", "cand_office_district"),
    ),

    /** Committee master (`cm`): one row per registered committee. */
    COMMITTEE(
        "cm", "cm.txt", 15, "staging_committee",
        listOf("cmte_id", "cmte_nm", "cmte_tp", "cmte_dsgn"),
    ),

    /** Candidate–committee linkage (`ccl`): which committees belong to which candidates. */
    LINKAGE(
        "ccl", "ccl.txt", 7, "staging_linkage",
        listOf("cand_id", "cmte_id", "cmte_tp", "cmte_dsgn", "linkage_id"),
    ),

    /**
     * Itemized individual contributions (`indiv`, extracted as `itcont.txt`):
     * one row per itemized donation. By far the largest file — ~25M rows /
     * 1.7 GB zipped for the 2026 cycle.
     */
    CONTRIBUTIONS(
        "indiv", "itcont.txt", 21, "staging_contribution",
        listOf(
            "sub_id", "cmte_id", "contributor_name", "city", "state",
            "zip_code", "employer", "occupation", "transaction_dt", "transaction_amt",
        ),
    ),
    ;

    /** Zip file name for a cycle, e.g. `cn26.zip` for cycle 2026. */
    fun zipName(cycle: Int): String = "$key${"%02d".format(cycle % 100)}.zip"

    /** Full download URL for a cycle. fec.gov 302-redirects this to S3. */
    fun url(cycle: Int): String = "https://www.fec.gov/files/bulk-downloads/$cycle/${zipName(cycle)}"
}
