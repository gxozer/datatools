package com.campaignfinances.pipeline.ingestion

/**
 * Credentials for `api.open.fec.gov`.
 *
 * Built from the `FEC_API_KEY` environment variable, mirroring
 * [com.campaignfinances.pipeline.db.DbConfig.fromEnv] — never hardcoded, never
 * committed (see `.gitignore`'s secrets section).
 *
 * @property apiKey the FEC API key, sent as the `api_key` query parameter on
 *   every request
 */
data class FecApiConfig(val apiKey: String) {
    companion object {
        /**
         * @param env the environment map; injectable so tests can pass a plain
         *   map instead of mutating real process state
         * @throws IllegalStateException if `FEC_API_KEY` is not set — unlike
         *   [com.campaignfinances.pipeline.db.DbConfig], there is no safe local
         *   default for a real secret
         */
        fun fromEnv(env: Map<String, String> = System.getenv()): FecApiConfig {
            val apiKey = env["FEC_API_KEY"]
            check(!apiKey.isNullOrBlank()) { "FEC_API_KEY environment variable is not set" }
            return FecApiConfig(apiKey)
        }
    }
}
