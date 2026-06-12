package com.campaignfinances.pipeline.db

data class DbConfig(
    val url: String,
    val user: String,
    val password: String,
) {
    companion object {
        fun fromEnv(env: Map<String, String> = System.getenv()): DbConfig = DbConfig(
            url = env["CF_DB_URL"]
                ?: "jdbc:mysql://localhost:3307/campaign_finances?allowLoadLocalInfile=true",
            user = env["CF_DB_USER"] ?: "cf",
            password = env["CF_DB_PASSWORD"] ?: "cf",
        )
    }
}
