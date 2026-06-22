package com.campaignfinances.pipeline.cli

/**
 * Dispatches command-line arguments to the matching [Command].
 *
 * Behavior:
 * - no arguments        → print usage to [out], exit 0
 * - unknown command     → print error + usage to [err], exit 1
 * - known command       → delegate to [Command.run] with the remaining args
 *
 * @param commands the available subcommands, in the order they should appear
 *   in the usage listing
 * @param out where normal output goes; defaults to stdout. Injectable so tests
 *   can capture output in a [StringBuilder] instead of the real console.
 * @param err where error output goes; defaults to stderr.
 */
class Cli(
    private val commands: List<Command>,
    private val out: Appendable = System.out,
    private val err: Appendable = System.err,
) {

    /**
     * Runs the command named by the first argument and returns its exit code.
     *
     * @param args the raw process arguments (command name first)
     * @return the exit code to terminate the process with
     */
    fun run(args: Array<String>): Int {
        // No arguments at all: asking for help is not an error.
        if (args.isEmpty()) {
            printUsage(out)
            return 0
        }

        val commandName = args.first()
        val command = commands.find { it.name == commandName }
        if (command == null) {
            err.appendLine("Unknown command: $commandName")
            printUsage(err)
            return 1
        }

        // The command receives only its own arguments, not its name.
        return command.run(args.drop(1))
    }

    /** Writes the usage listing (one aligned line per command) to [target]. */
    private fun printUsage(target: Appendable) {
        target.appendLine("Campaign Finances data pipeline")
        target.appendLine()
        target.appendLine("Usage: pipeline <command> [options]")
        target.appendLine()
        target.appendLine("Commands:")
        // Pad command names so the descriptions line up in a column.
        val width = commands.maxOf { it.name.length }
        for (command in commands) {
            target.appendLine("  ${command.name.padEnd(width + 2)}${command.description}")
        }
    }
}
