package com.campaignfinances.pipeline.cli

// Implemented by PR-158
class ReconcileCommand : Command {

    override val name = "reconcile"
    override val description = "Compare our totals against FEC.gov published totals (--candidates=<fec-ids>)"

    override fun run(args: List<String>): Int {
        System.err.println("'$name' is not implemented yet")
        return 2
    }
}
