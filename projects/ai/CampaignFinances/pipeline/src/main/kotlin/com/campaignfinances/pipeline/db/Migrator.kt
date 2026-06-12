package com.campaignfinances.pipeline.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

class Migrator(private val config: DbConfig) {

    fun migrate(): MigrateResult = Flyway.configure()
        .dataSource(config.url, config.user, config.password)
        .load()
        .migrate()
}
