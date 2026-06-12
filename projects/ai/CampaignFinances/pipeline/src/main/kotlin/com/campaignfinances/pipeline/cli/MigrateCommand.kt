package com.campaignfinances.pipeline.cli

import com.campaignfinances.pipeline.db.Migrator

class MigrateCommand(
    private val migrator: Migrator,
    private val out: Appendable = System.out,
) : Command {

    override val name = "migrate"
    override val description = "Apply Flyway schema migrations"

    override fun run(args: List<String>): Int {
        val result = migrator.migrate()
        out.appendLine("Applied ${result.migrationsExecuted} migration(s)")
        return if (result.success) 0 else 1
    }
}
