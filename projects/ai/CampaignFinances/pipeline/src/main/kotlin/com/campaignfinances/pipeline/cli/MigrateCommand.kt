package com.campaignfinances.pipeline.cli

import com.campaignfinances.pipeline.db.Migrator

/**
 * `migrate` — applies pending Flyway schema migrations to the configured database.
 *
 * Safe to run repeatedly: Flyway tracks applied migrations in its
 * `flyway_schema_history` table, so a second run applies nothing
 * (see docs/TDS_PHASE1.md and the Flyway notes in PR-152).
 *
 * @param migrator performs the actual migration; injected so tests could
 *   substitute a fake without a database
 * @param out where the result summary is printed
 */
class MigrateCommand(
    private val migrator: Migrator,
    private val out: Appendable = System.out,
) : Command {

    override val name = "migrate"
    override val description = "Apply Flyway schema migrations"

    /**
     * Runs the migration and reports how many migrations were applied.
     * @return 0 on success, 1 if Flyway reports failure
     */
    override fun run(args: List<String>): Int {
        val result = migrator.migrate()
        out.appendLine("Applied ${result.migrationsExecuted} migration(s)")
        return if (result.success) 0 else 1
    }
}
