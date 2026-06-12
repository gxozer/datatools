package com.campaignfinances.pipeline.db

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class DbConfigTest {

    @Test
    fun `uses local docker compose defaults when env is empty`() {
        val config = DbConfig.fromEnv(emptyMap())

        assertEquals("jdbc:mysql://localhost:3307/campaign_finances", config.url)
        assertEquals("cf", config.user)
        assertEquals("cf", config.password)
    }

    @Test
    fun `environment variables override every default`() {
        val config = DbConfig.fromEnv(
            mapOf(
                "CF_DB_URL" to "jdbc:mysql://db.example.com:3306/cf",
                "CF_DB_USER" to "svc",
                "CF_DB_PASSWORD" to "secret",
            ),
        )

        assertEquals("jdbc:mysql://db.example.com:3306/cf", config.url)
        assertEquals("svc", config.user)
        assertEquals("secret", config.password)
    }
}
