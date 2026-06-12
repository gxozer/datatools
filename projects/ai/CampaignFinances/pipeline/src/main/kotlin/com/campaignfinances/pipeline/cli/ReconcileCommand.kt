package com.campaignfinances.pipeline.cli

// Implemented by PR-158
class ReconcileCommand(private val err: Appendable = System.err) : Command {

    override val name = "reconcile"
    override val description = "Compare our totals against FEC.gov published totals (--candidates=<fec-ids>)"

    override fun run(args: List<String>): Int {
        err.appendLine("'$name' is not implemented yet")
        return 2
    }
}
