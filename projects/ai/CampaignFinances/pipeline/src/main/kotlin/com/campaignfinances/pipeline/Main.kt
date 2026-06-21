package com.campaignfinances.pipeline

import com.campaignfinances.pipeline.cli.Cli
import com.campaignfinances.pipeline.cli.DedupCommand
import com.campaignfinances.pipeline.cli.IngestCommand
import com.campaignfinances.pipeline.cli.MigrateCommand
import com.campaignfinances.pipeline.cli.ReconcileCommand
import com.campaignfinances.pipeline.db.DbConfig
import com.campaignfinances.pipeline.db.Migrator
import kotlin.system.exitProcess

/**
 * Entry point of the Campaign Finances data pipeline CLI.
 *
 * This is the composition root: the one place where production dependencies are
 * constructed and wired together. Database configuration comes from environment
 * variables (`CF_DB_URL`, `CF_DB_USER`, `CF_DB_PASSWORD`) with local-dev defaults
 * pointing at the Docker Compose MySQL (see [DbConfig.fromEnv]).
 *
 * Invocation (from the project root):
 * ```
 * ./gradlew :pipeline:run --args="<command> [options]"
 * ```
 *
 * Commands: `migrate`, `ingest`, `dedup` (PR-156), `reconcile` (PR-158).
 * The process exit code is whatever the executed command returns
 * (0 = success, 1 = failure, 2 = usage error / not implemented).
 */
fun main(args: Array<String>) {
    val dbConfig = DbConfig.fromEnv()
    val cli = Cli(
        commands = listOf(
            MigrateCommand(Migrator(dbConfig)),
            IngestCommand(dbConfig),
            DedupCommand(),
            ReconcileCommand(),
        ),
    )
    exitProcess(cli.run(args))
}
