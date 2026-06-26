package com.campaignfinances.pipeline.ingestion

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Mocked-HTTP unit tests for [FecApiClient]: response parsing, pagination
 * cursor handling, and the throttle/backoff rate-limit mechanisms — no real
 * network call and no real waiting ([FecApiClient.sleep]/`now` are injected
 * fakes throughout).
 */
class FecApiClientTest {

    private val onePageJson = """
        {
          "results": [
            {
              "sub_id": 4041520261234567890,
              "committee_id": "C00386987",
              "contributor_name": "DOE, JANE",
              "contributor_city": "ANYTOWN",
              "contributor_state": "CA",
              "contributor_zip": "900121234",
              "contributor_employer": "ANY EMPLOYER",
              "contributor_occupation": "ANY OCCUPATION",
              "contribution_receipt_date": "2026-03-15T00:00:00+00:00",
              "contribution_receipt_amount": 500.0
            }
          ],
          "pagination": { "page": 1, "pages": 1, "last_indexes": null }
        }
    """.trimIndent()

    @Test
    fun `fetchPage parses results and pagination from the response body`() {
        val client = clientReturning { onePageJson }

        val page = runBlocking { client.fetchPage("2026-01-01", 2026, cursor = null) }

        val result = page.results.single()
        assertEquals(4041520261234567890L, result.subId)
        assertEquals("C00386987", result.committeeId)
        assertEquals("DOE, JANE", result.contributorName)
        assertEquals(500.0, result.contributionReceiptAmount)
        assertEquals(1, page.pagination.page)
        assertEquals(1, page.pagination.pages)
        assertNull(page.pagination.lastIndexes)
    }

    @Test
    fun `fetchPage sends min_date and omits cursor params on the first page`() {
        var seenParams: Parameters? = null
        val client = clientCapturingRequest({ seenParams = it.url.parameters }) { onePageJson }

        runBlocking { client.fetchPage("2026-02-01", 2026, cursor = null) }

        assertEquals("02/01/2026", seenParams?.get("min_date"))
        assertNull(seenParams?.get("last_index"))
    }

    @Test
    fun `fetchPage sends the cursor's keyset params on a later page`() {
        var seenParams: Parameters? = null
        val client = clientCapturingRequest({ seenParams = it.url.parameters }) { onePageJson }
        val cursor = LastIndexes(lastIndex = "4041520261234567890", lastContributionReceiptDate = "2026-03-15")

        runBlocking { client.fetchPage("2026-02-01", 2026, cursor) }

        assertEquals("4041520261234567890", seenParams?.get("last_index"))
        assertEquals("2026-03-15", seenParams?.get("last_contribution_receipt_date"))
    }

    @Test
    fun `fetchPage retries with the Retry-After delay on 429 and then succeeds`() {
        var attempts = 0
        val sleeps = mutableListOf<Long>()
        val engine = MockEngine { _ ->
            attempts++
            if (attempts == 1) {
                respond(content = "", status = HttpStatusCode.TooManyRequests, headers = headersOf("Retry-After", "7"))
            } else {
                respondJson(onePageJson)
            }
        }
        val client = FecApiClient(jsonHttpClient(engine), apiKey = "test-key", sleep = { sleeps.add(it) }, now = { 0L })

        val page = runBlocking { client.fetchPage("2026-01-01", 2026, cursor = null) }

        assertEquals(2, attempts)
        assertEquals(listOf(7_000L), sleeps)
        assertEquals(1, page.results.size)
    }

    @Test
    fun `fetchPage gives up after maxRetries consecutive 429s`() {
        val engine = MockEngine { _ -> respond(content = "", status = HttpStatusCode.TooManyRequests) }
        val client = FecApiClient(jsonHttpClient(engine), apiKey = "test-key", maxRetries = 2, sleep = {}, now = { 0L })

        val outcome = runCatching { runBlocking { client.fetchPage("2026-01-01", 2026, cursor = null) } }

        assertTrue(outcome.isFailure)
        assertTrue(outcome.exceptionOrNull()?.message.orEmpty().contains("rate limit"))
    }

    @Test
    fun `fetchPage throttles a second call to at least minIntervalMillis after the first`() {
        var clock = 0L
        val sleeps = mutableListOf<Long>()
        val client = FecApiClient(
            httpClient = jsonHttpClient(MockEngine { _ -> respondJson(onePageJson) }),
            apiKey = "test-key",
            minIntervalMillis = 3_600,
            sleep = { millis -> sleeps.add(millis); clock += millis },
            now = { clock },
        )

        runBlocking {
            client.fetchPage("2026-01-01", 2026, cursor = null)
            client.fetchPage("2026-01-01", 2026, cursor = null)
        }

        // No sleep on the first call (nothing to throttle against yet); the
        // second call must wait out the remaining interval.
        assertEquals(listOf(3_600L), sleeps)
    }

    private fun jsonHttpClient(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
    }

    private fun MockRequestHandleScope.respondJson(body: String) = respond(
        content = body,
        status = HttpStatusCode.OK,
        headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
    )

    private fun clientReturning(body: () -> String): FecApiClient {
        val engine = MockEngine { _ -> respondJson(body()) }
        return FecApiClient(jsonHttpClient(engine), apiKey = "test-key", sleep = {}, now = { 0L })
    }

    private fun clientCapturingRequest(onRequest: (HttpRequestData) -> Unit, body: () -> String): FecApiClient {
        val engine = MockEngine { request ->
            onRequest(request)
            respondJson(body())
        }
        return FecApiClient(jsonHttpClient(engine), apiKey = "test-key", sleep = {}, now = { 0L })
    }
}
