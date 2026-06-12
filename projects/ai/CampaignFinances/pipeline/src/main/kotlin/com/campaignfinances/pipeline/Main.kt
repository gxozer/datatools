package com.campaignfinances.pipeline

import com.campaignfinances.pipeline.cli.Cli
import com.campaignfinances.pipeline.cli.DedupCommand
import com.campaignfinances.pipeline.cli.IngestCommand
import com.campaignfinances.pipeline.cli.MigrateCommand
import com.campaignfinances.pipeline.cli.ReconcileCommand
import com.campaignfinances.pipeline.db.DbConfig
import com.campaignfinances.pipeline.db.Migrator
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val dbConfig = DbConfig.fromEnv()
    val cli = Cli(
        commands = listOf(
            MigrateCommand(Migrator(dbConfig)),
            IngestCommand(),
            DedupCommand(),
            ReconcileCommand(),
        ),
    )
    exitProcess(cli.run(args))
}
