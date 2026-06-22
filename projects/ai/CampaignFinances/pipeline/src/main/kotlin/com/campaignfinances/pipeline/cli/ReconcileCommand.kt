package com.campaignfinances.pipeline.cli

/**
 * `reconcile` — compares our aggregated totals against FEC.gov published totals.
 *
 * **Stub.** The real implementation arrives with PR-158: a repeatable pass/fail
 * report for at least 5 candidates, the trust-establishing half of the Phase 1
 * demo gate (see docs/TDS_PHASE1.md §6).
 *
 * @param err where the not-implemented message is printed
 */
class ReconcileCommand(private val err: Appendable = System.err) : Command {

    override val name = "reconcile"
    override val description = "Compare our totals against FEC.gov published totals (--candidates=<fec-ids>)"

    /** Always exits 2 (not implemented) until PR-158 lands. */
    override fun run(args: List<String>): Int {
        err.appendLine("'$name' is not implemented yet")
        return 2
    }
}
