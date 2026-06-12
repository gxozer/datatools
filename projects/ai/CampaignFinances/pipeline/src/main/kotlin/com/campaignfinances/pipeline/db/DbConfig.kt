package com.campaignfinances.pipeline.db

data class DbConfig(
    val url: String,
    val user: String,
    val password: String,
) {
    companion object {
        // allowLoadLocalInfile is NOT set here: only the bulk-load path needs it,
        // and it enables that capability per-connection (see PR-153)
        fun fromEnv(env: Map<String, String> = System.getenv()): DbConfig = DbConfig(
            url = env["CF_DB_URL"]
                ?: "jdbc:mysql://localhost:3307/campaign_finances",
            user = env["CF_DB_USER"] ?: "cf",
            password = env["CF_DB_PASSWORD"] ?: "cf",
        )
    }
}
