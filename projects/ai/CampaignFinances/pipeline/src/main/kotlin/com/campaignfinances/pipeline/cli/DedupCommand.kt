package com.campaignfinances.pipeline.cli

/**
 * `dedup` — donor de-duplication over canonical data.
 *
 * **Stub.** The real implementation arrives with PR-156: deterministic,
 * conservative donor matching with a `donor_link` audit trail
 * (see docs/TDS_PHASE1.md §5).
 *
 * @param err where the not-implemented message is printed
 */
class DedupCommand(private val err: Appendable = System.err) : Command {

    override val name = "dedup"
    override val description = "Run donor de-duplication over canonical data"

    /** Always exits 2 (not implemented) until PR-156 lands. */
    override fun run(args: List<String>): Int {
        err.appendLine("'$name' is not implemented yet")
        return 2
    }
}
