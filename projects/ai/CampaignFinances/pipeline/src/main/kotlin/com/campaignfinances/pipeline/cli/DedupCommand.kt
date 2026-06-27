package com.campaignfinances.pipeline.cli

import com.campaignfinances.pipeline.db.DbConfig
import com.campaignfinances.pipeline.dedup.DedupRunner

/**
 * `dedup` — donor de-duplication over canonical contribution data (PR-156,
 * docs/TDS_PHASE1.md §5).
 *
 * Reads all rows from `staging_contribution`, normalizes name/employer/zip,
 * groups by the conservative match key `(last_name, first_name, zip5,
 * employer)`, and writes `donor` + `donor_link` rows. Re-runnable without
 * re-ingest: clears previous results before rebuilding.
 *
 * **Prerequisite:** `staging_contribution` must be populated by a prior
 * `ingest --source=fec-bulk` or `ingest --source=fec-api` run.
 *
 * @param dbConfig connection settings passed through to [DedupRunner]
 * @param out where progress and the final summary are printed
 */
class DedupCommand(
    private val dbConfig: DbConfig,
    private val out: Appendable = System.out,
) : Command {

    override val name = "dedup"
    override val description = "De-duplicate donors across canonical contributions (requires prior ingest)"

    /**
     * Runs the dedup job and prints a one-line summary with the result counts.
     *
     * @param args unused — dedup takes no options in Phase 1
     * @return 0 on success; any exception propagates and the process exits non-zero
     */
    override fun run(args: List<String>): Int {
        val summary = DedupRunner(dbConfig, out).run()
        out.appendLine(
            "dedup complete: ${summary.donorsCreated} donors, " +
                "${summary.linksCreated} links, ${summary.skipped} skipped",
        )
        return 0
    }
}
