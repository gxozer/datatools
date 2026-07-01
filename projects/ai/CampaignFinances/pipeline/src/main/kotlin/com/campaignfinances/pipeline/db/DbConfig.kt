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
     * The JDBC URL with MySQL session variables that prevent the server from
     * closing a long-running write connection mid-batch (PR-156 pass 2).
     *
     * Dedup pass 2 holds an open transaction for hours while batch-writing
     * 25M rows. Without overrides, MySQL's default `wait_timeout=28800s` (8 h)
     * and `net_write_timeout=60s` can kill the connection mid-run. Setting them
     * to 86 400 s (24 h) gives the write connection safe headroom.
     *
     * Use this URL **only** for the dedup write connection. Normal connections
     * should use [url] directly.
     */
    val urlForLongRunningWrite: String
        get() = url + (if ('?' in url) "&" else "?") +
            "sessionVariables=wait_timeout=86400,interactive_timeout=86400,net_write_timeout=86400"

    /**
     * Opens a JDBC connection suitable for long-running batched writes (hours).
     *
     * Uses [urlForLongRunningWrite] to raise MySQL session-level timeouts to
     * 24 h so the server does not close the write connection mid-batch during
     * dedup pass 2.
     *
     * @return a new open [Connection]
     */
    fun openLongRunningConnection(): Connection = DriverManager.getConnection(urlForLongRunningWrite, user, password)

    /**
     * The JDBC URL with `netTimeoutForStreamingResults=3600` appended.
     *
     * When `fetchSize = Int.MIN_VALUE` activates MySQL row-at-a-time streaming,
     * Connector/J issues `SET net_write_timeout = N` on the session, where N is
     * `netTimeoutForStreamingResults` (default: 600 s). If a batch write on the
     * write connection blocks the `rs.next()` loop for longer than that, MySQL
     * closes the streaming socket and `rs.close()` throws "Socket is closed".
     * 3 600 s (1 h) gives dedup batch writes ample headroom (PR-208).
     *
     * Use this URL **only** for read-only streaming connections in dedup passes.
     */
    val urlForStreaming: String
        get() = url + (if ('?' in url) "&" else "?") + "netTimeoutForStreamingResults=3600"

    /**
     * Opens a standard JDBC connection using [url].
     *
     * For bulk loads that need `LOAD DATA LOCAL INFILE` use [urlWithLocalInfile]
     * directly (see [com.campaignfinances.pipeline.ingestion.FecBulkAdapter]).
     * For row-at-a-time streaming ResultSets use [openStreamingConnection].
     *
     * @return a new open [Connection]
     */
    fun openConnection(): Connection = DriverManager.getConnection(url, user, password)

    /**
     * Opens a JDBC connection suitable for row-at-a-time streaming ResultSets
     * (`fetchSize = Int.MIN_VALUE`).
     *
     * Uses [urlForStreaming] to raise `net_write_timeout` to 3 600 s so MySQL
     * does not close the socket if a batch write on the write connection delays
     * the next `rs.next()` call beyond the default 600 s limit.
     *
     * @return a new open [Connection]
     */
    fun openStreamingConnection(): Connection = DriverManager.getConnection(urlForStreaming, user, password)

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
