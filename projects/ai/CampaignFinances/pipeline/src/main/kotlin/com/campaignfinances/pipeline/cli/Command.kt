package com.campaignfinances.pipeline.cli

interface Command {
    val name: String
    val description: String

    /** Executes the command and returns a process exit code. */
    fun run(args: List<String>): Int
}
