package com.campaignfinances.pipeline.ingestion

import java.nio.file.Path

/**
 * What the CLI needs from a bulk ingest; [FecBulkAdapter] is the production
 * implementation.
 *
 * Declared as a `fun interface` (single abstract method) so tests can supply a
 * fake with lambda syntax: `BulkIngestRunner { cycle, dir -> ... }`.
 */
fun interface BulkIngestRunner {

    /**
     * Loads one election cycle of FEC bulk data into staging, then normalizes
     * it into the canonical schema (docs/TDS_PHASE1.md §4) in the same run.
     *
     * @param cycle the two-year federal election cycle, e.g. 2026
     * @param localDir if non-null, read already-extracted `.txt` files from
     *   this directory instead of downloading (tests / offline runs)
     * @return per-file row counts and the ingest_run id (canonical-stage
     *   counts are recorded in `ingest_run.row_counts` but not returned here)
     */
    fun ingest(cycle: Int, localDir: Path?): IngestSummary
}
