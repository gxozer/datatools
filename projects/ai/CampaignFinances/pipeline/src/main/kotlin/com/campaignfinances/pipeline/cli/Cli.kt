package com.campaignfinances.pipeline.cli

class Cli(
    private val commands: List<Command>,
    private val out: Appendable = System.out,
    private val err: Appendable = System.err,
) {

    fun run(args: Array<String>): Int {
        val commandName = args.firstOrNull() ?: run {
            printUsage(out)
            return 0
        }
        val command = commands.find { it.name == commandName } ?: run {
            err.appendLine("Unknown command: $commandName")
            printUsage(err)
            return 1
        }
        return command.run(args.drop(1))
    }

    private fun printUsage(target: Appendable) {
        target.appendLine("Campaign Finances data pipeline")
        target.appendLine()
        target.appendLine("Usage: pipeline <command> [options]")
        target.appendLine()
        target.appendLine("Commands:")
        val width = commands.maxOf { it.name.length }
        commands.forEach { target.appendLine("  ${it.name.padEnd(width + 2)}${it.description}") }
    }
}
