package com.campaignfinances.pipeline.cli

// Implemented by PR-156
class DedupCommand : Command {

    override val name = "dedup"
    override val description = "Run donor de-duplication over canonical data"

    override fun run(args: List<String>): Int {
        System.err.println("'$name' is not implemented yet")
        return 2
    }
}
