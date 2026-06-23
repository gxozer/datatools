package com.campaignfinances.pipeline.ingestion

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay

/**
 * Thin wrapper over `GET /v1/schedules/schedule_a/` on api.open.fec.gov.
 *
 * Two rate-limit mechanisms, per docs/TDS_PHASE1.md §7 ("API adapter is for
 * incremental top-ups only — rate limit 1,000 calls/hour"):
 * - **Throttle:** waits [minIntervalMillis] between consecutive calls,
 *   pacing requests under the limit instead of bursting and reacting only
 *   after the fact.
 * - **Backoff:** on HTTP 429, retries using the `Retry-After` header's delay
 *   (or an exponential fallback if the header is absent), up to [maxRetries]
 *   times before giving up.
 *
 * Pagination uses FEC's keyset cursor (`last_index` +
 * `last_contribution_receipt_date`) rather than page numbers — FEC's API
 * docs note page-number pagination degrades badly past a few thousand pages.
 *
 * @param httpClient must have `ContentNegotiation` + JSON installed; tests
 *   inject a `MockEngine`-backed client instead of a real network call
 * @param apiKey sent as the `api_key` query parameter on every request
 * @param baseUrl overridable for tests
 * @param minIntervalMillis minimum spacing enforced between consecutive calls
 * @param maxRetries how many 429 retries one page tolerates before giving up
 * @param sleep injected delay function so tests don't actually wait through
 *   throttling or backoff
 * @param now injected clock so throttle timing is deterministic in tests
 */
class FecApiClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.open.fec.gov/v1/schedules/schedule_a/",
    private val minIntervalMillis: Long = 3_600_000L / 1_000,
    private val maxRetries: Int = 5,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var lastRequestAtMillis: Long? = null

    /**
     * Fetches one page of contributions with `contribution_receipt_date >= minDate`,
     * sorted oldest-first so the keyset cursor advances monotonically.
     *
     * @param minDate the watermark, formatted `YYYY-MM-DD`
     * @param cursor the previous page's [LastIndexes], or null to request the first page
     */
    suspend fun fetchPage(minDate: String, cursor: LastIndexes?): ScheduleAResponse {
        throttle()
        var attempt = 0
        while (true) {
            val response = httpClient.get(baseUrl) {
                parameter("api_key", apiKey)
                parameter("min_date", minDate)
                parameter("sort", "contribution_receipt_date")
                parameter("sort_hide_null", "true")
                parameter("per_page", 100)
                if (cursor != null) {
                    parameter("last_index", cursor.lastIndex)
                    parameter("last_contribution_receipt_date", cursor.lastContributionReceiptDate)
                }
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                attempt++
                check(attempt <= maxRetries) { "FEC API rate limit exceeded after $maxRetries retries" }
                val retryAfterMillis = (response.headers["Retry-After"]?.toLongOrNull() ?: (1L shl attempt)) * 1000
                sleep(retryAfterMillis)
                continue
            }
            check(response.status.isSuccess()) { "GET $baseUrl failed: ${response.status}" }
            return response.body()
        }
    }

    /** Sleeps just long enough since the last call to keep spacing at [minIntervalMillis]. */
    private suspend fun throttle() {
        val last = lastRequestAtMillis
        if (last != null) {
            val elapsed = now() - last
            if (elapsed < minIntervalMillis) sleep(minIntervalMillis - elapsed)
        }
        lastRequestAtMillis = now()
    }
}
