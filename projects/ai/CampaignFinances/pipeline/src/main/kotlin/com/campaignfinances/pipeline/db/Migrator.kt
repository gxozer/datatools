package com.campaignfinances.pipeline.db

import org.flywaydb.core.Flyway
import org.flywaydb.core.api.output.MigrateResult

/**
 * Applies Flyway schema migrations to the configured database.
 *
 * Migrations are discovered at Flyway's default classpath location,
 * `db/migration` (i.e. `pipeline/src/main/resources/db/migration/V*.sql`).
 * Applied versions are tracked in the `flyway_schema_history` table, so calling
 * [migrate] repeatedly is safe — already-applied migrations are skipped and
 * edited migrations are rejected via checksum validation.
 *
 * Per the TDS, each migration file is small and single-statement because MySQL
 * DDL commits implicitly (a failed multi-statement migration could otherwise
 * leave the schema half-applied).
 *
 * @param config connection settings; the plain URL is sufficient — migrations
 *   never need LOCAL INFILE
 */
class Migrator(private val config: DbConfig) {

    /**
     * Applies all pending migrations.
     * @return Flyway's result, including [MigrateResult.migrationsExecuted]
     *   and [MigrateResult.success]
     */
    fun migrate(): MigrateResult = Flyway.configure()
        .dataSource(config.url, config.user, config.password)
        .load()
        .migrate()
}
