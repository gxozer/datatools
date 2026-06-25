package com.campaignfinances.pipeline.db

import java.sql.Connection
import java.sql.DriverManager

/**
 * Database connection settings for the pipeline.
 *
 * Built from environment variables via [fromEnv]; defaults target the local
 * Docker Compose MySQL (`docker compose up -d --wait` from the project root).
 * Note the default port is **3307**, not 3306 — dev machines commonly run an
 * unrelated local mysqld on 3306.
 *
 * @property url JDBC URL of the campaign_finances database
 * @property user database user
 * @property password database password
 */
data class DbConfig(
    val url: String,
    val user: String,
    val password: String,
) {
    /**
     * The JDBC URL with `allowLoadLocalInfile=true` appended.
     *
     * `LOAD DATA LOCAL INFILE` is a powerful capability, so it is **not** part
     * of the default URL — only the bulk-load path opts in, per the security
     * decision on PR-164. Use this for [com.campaignfinances.pipeline.ingestion.StagingLoader]
     * connections and nothing else.
     */
    val urlWithLocalInfile: String
        get() = url + (if ('?' in url) "&" else "?") + "allowLoadLocalInfile=true"

    /**
     * Opens a standard JDBC connection using [url].
     *
     * For bulk loads that need `LOAD DATA LOCAL INFILE` use [urlWithLocalInfile]
     * directly (see [com.campaignfinances.pipeline.ingestion.FecBulkAdapter]).
     *
     * @return a new open [Connection]
     */
    fun openConnection(): Connection = DriverManager.getConnection(url, user, password)

    companion object {
        /**
         * Builds a config from environment variables, falling back to the
         * local-dev defaults for any that are unset:
         *
         * | Variable         | Default                                          |
         * |------------------|--------------------------------------------------|
         * | `CF_DB_URL`      | `jdbc:mysql://localhost:3307/campaign_finances`  |
         * | `CF_DB_USER`     | `cf`                                             |
         * | `CF_DB_PASSWORD` | `cf`                                             |
         *
         * @param env the environment map; injectable so tests can pass a plain
         *   map instead of mutating real process state
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): DbConfig = DbConfig(
            url = env["CF_DB_URL"]
                ?: "jdbc:mysql://localhost:3307/campaign_finances",
            user = env["CF_DB_USER"] ?: "cf",
            password = env["CF_DB_PASSWORD"] ?: "cf",
        )
    }
}
