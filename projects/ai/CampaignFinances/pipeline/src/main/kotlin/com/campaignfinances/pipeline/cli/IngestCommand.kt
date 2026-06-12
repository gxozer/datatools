package com.campaignfinances.pipeline.cli

// Implemented by PR-153 (fec-bulk) and PR-155 (fec-api)
class IngestCommand(private val err: Appendable = System.err) : Command {

    override val name = "ingest"
    override val description = "Load FEC data into staging and canonical schema (--source=fec-bulk|fec-api [--cycle=2026])"

    override fun run(args: List<String>): Int {
        err.appendLine("'$name' is not implemented yet")
        return 2
    }
}
