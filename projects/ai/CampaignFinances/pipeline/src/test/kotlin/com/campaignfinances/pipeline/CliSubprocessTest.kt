package com.campaignfinances.pipeline

import com.campaignfinances.pipeline.db.DbConfig
import com.campaignfinances.pipeline.ingestion.queryLong
import org.junit.jupiter.api.Test
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.file.Path
import java.sql.DriverManager
import java.util.concurrent.TimeUnit
import kotlin.test.assertContains
import kotlin.test.assertEquals

/**
 * Runs the actual CLI as a separate OS process — every other test in this
 * suite, including the "integration" ones, calls Kotlin classes directly
 * inside the test JVM. This is the one tier that exercises [main]'s
 * `exitProcess`, the real packaged classpath, and real
 * `CF_DB_URL`/`CF_DB_USER`/`CF_DB_PASSWORD` environment-variable wiring
 * ([DbConfig.fromEnv]) — a classpath or env-var mistake would pass every
 * in-process test here and still break in production.
 *
 * Complementary to, not a replacement for, the cross-stage end-to-end suite
 * (PR-159): that suite chains every pipeline stage but still calls Kotlin
 * classes in-process; this one is cross-*process* instead — see
 * docs/TEST_PLAN_PHASE1.md §4.7.
 */
@Testcontainers
class CliSubprocessTest {

    companion object {
        @Container
        @JvmStatic
        val mysql = MySQLContainer("mysql:8.4").withCommand("mysqld", "--local-infile=1")
    }

    @Test
    fun `running the built CLI as a real process migrates and ingests against real env vars`() {
        val fixtures = Path.of(javaClass.getResource("/fixtures/fec-bulk")!!.toURI()).toString()
        val env = mapOf(
            "CF_DB_URL" to mysql.jdbcUrl,
            "CF_DB_USER" to mysql.username,
            "CF_DB_PASSWORD" to mysql.password,
        )

        val migrateResult = runCli(env, "migrate")
        assertEquals(0, migrateResult.exitCode, "migrate exit code; output was:\n${migrateResult.output}")

        // Fixture totals (see FecBulkIngestIntegrationTest): cn/cm/ccl are
        // (loaded=3, bad=1) each, indiv is (loaded=3, bad=2).
        val ingestResult = runCli(env, "ingest", "--source=fec-bulk", "--cycle=2026", "--dir=$fixtures")
        assertEquals(0, ingestResult.exitCode, "ingest exit code; output was:\n${ingestResult.output}")
        assertContains(ingestResult.output, "complete: 12 rows loaded, 5 bad rows skipped")

        val config = DbConfig(mysql.jdbcUrl, mysql.username, mysql.password)
        DriverManager.getConnection(config.url, config.user, config.password).use { connection ->
            assertEquals(3, connection.queryLong("SELECT COUNT(*) FROM candidate"))
            assertEquals(3, connection.queryLong("SELECT COUNT(*) FROM contribution"))
        }
    }

    private data class ProcessResult(val exitCode: Int, val output: String)

    /**
     * Spawns `java -cp <classpath> com.campaignfinances.pipeline.MainKt <args>`
     * as a genuine child process (no shell involved — [ProcessBuilder] takes
     * each argument literally) with [env] as additional environment
     * variables, and waits for it to exit.
     *
     * Reuses this test JVM's own classpath (`java.class.path`): Gradle's test
     * runtime classpath is a superset of the main runtime classpath, so
     * `MainKt` and everything it needs to run are already on it — no need to
     * shell out to Gradle itself just to assemble one.
     */
    private fun runCli(env: Map<String, String>, vararg args: String): ProcessResult {
        val javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val classpath = System.getProperty("java.class.path")
        val command = listOf(javaBin, "-cp", classpath, "com.campaignfinances.pipeline.MainKt") + args

        val process = ProcessBuilder(command)
            .redirectErrorStream(true)
            .also { it.environment().putAll(env) }
            .start()

        // Drain stdout before waitFor: a full pipe buffer would otherwise
        // deadlock the child process against this read.
        val output = process.inputStream.bufferedReader().readText()
        val finished = process.waitFor(60, TimeUnit.SECONDS)
        check(finished) { "CLI subprocess did not exit within 60s: ${command.joinToString(" ")}" }
        return ProcessResult(process.exitValue(), output)
    }
}
