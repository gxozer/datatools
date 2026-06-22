package com.campaignfinances.pipeline.cli

/**
 * A single CLI subcommand (e.g. `migrate`, `ingest`).
 *
 * Implementations are registered with [Cli], which dispatches by [name].
 * One class per command keeps each command independently constructible and
 * testable; commands receive their dependencies (database config, output
 * streams) through their constructors rather than reaching for globals.
 */
interface Command {

    /** The token the user types to select this command (first CLI argument). */
    val name: String

    /** One-line, human-readable summary shown in the usage listing. */
    val description: String

    /**
     * Executes the command.
     *
     * @param args the command's own arguments — everything *after* the command
     *   name (e.g. for `pipeline ingest --source=fec-bulk`, args is
     *   `["--source=fec-bulk"]`)
     * @return the process exit code: 0 = success, 1 = failure,
     *   2 = usage error or not-yet-implemented
     */
    fun run(args: List<String>): Int
}
