package com.campaignfinances.pipeline.ingestion

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import mu.KotlinLogging
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val logger = KotlinLogging.logger {}

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
 * Set `CF_LOG_LEVEL=DEBUG` to log the full request URL on every page fetch.
 *
 * @param httpClient must have `ContentNegotiation` + JSON installed; tests
 *   inject a `MockEngine`-backed client instead of a real network call
 * @param apiKey sent as the `api_key` query parameter on every request
 * @param baseUrl overridable for tests
 * @param minIntervalMillis minimum spacing enforced between consecutive calls.
 *   Default leaves headroom under the 1,000/hour limit (PR-185) rather than
 *   pacing at exactly the limit with zero margin for clock drift or retries.
 * @param maxRetries how many 429 retries one page tolerates before giving up
 * @param sleep injected delay function so tests don't actually wait through
 *   throttling or backoff
 * @param now injected clock so throttle timing is deterministic in tests
 */
class FecApiClient(
    private val httpClient: HttpClient,
    private val apiKey: String,
    private val baseUrl: String = "https://api.open.fec.gov/v1/schedules/schedule_a/",
    private val minIntervalMillis: Long = 4_000L,
    private val maxRetries: Int = 5,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private var lastRequestAtMillis: Long? = null

    companion object {
        // FEC API expects MM/dd/yyyy, not ISO yyyy-MM-dd (returns 400 otherwise).
        private val FEC_DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy")
    }

    /**
     * Fetches one page of contributions with `contribution_receipt_date >= minDate`
     * in the given [twoYearPeriod] (required by the FEC API — queries without it
     * are rejected with 400), sorted oldest-first so the keyset cursor advances
     * monotonically.
     *
     * @param minDate the watermark, formatted `yyyy-MM-dd` (ISO); converted to
     *   `MM/dd/yyyy` internally before sending to the FEC API
     * @param twoYearPeriod the FEC two-year transaction period, e.g. 2026
     * @param cursor the previous page's [LastIndexes], or null to request the first page
     * @return the parsed page of results and pagination metadata
     */
    suspend fun fetchPage(minDate: String, twoYearPeriod: Int, cursor: LastIndexes?): ScheduleAResponse {
        throttle()
        val fecDate = LocalDate.parse(minDate).format(FEC_DATE_FORMAT)
        var attempt = 0
        while (true) {
            val response = try {
                val httpResponse = httpClient.get(baseUrl) {
                    parameter("api_key", apiKey)
                    parameter("two_year_transaction_period", twoYearPeriod)
                    parameter("min_date", fecDate)
                    parameter("sort", "contribution_receipt_date")
                    parameter("sort_hide_null", "true")
                    parameter("per_page", 100)
                    if (cursor != null) {
                        parameter("last_index", cursor.lastIndex)
                        parameter("last_contribution_receipt_date", cursor.lastContributionReceiptDate)
                    }
                }
                logger.debug { "GET ${httpResponse.call.request.url}" }
                httpResponse
            } catch (e: HttpRequestTimeoutException) {
                attempt++
                check(attempt <= maxRetries) { "FEC API timed out after $maxRetries retries" }
                val backoffMillis = (1L shl attempt) * 1000
                logger.warn { "request timeout (attempt $attempt/$maxRetries), retrying in ${backoffMillis}ms" }
                sleep(backoffMillis)
                continue
            }
            if (response.status == HttpStatusCode.TooManyRequests) {
                attempt++
                check(attempt <= maxRetries) { "FEC API rate limit exceeded after $maxRetries retries" }
                val retryAfterMillis = (response.headers["Retry-After"]?.toLongOrNull() ?: (1L shl attempt)) * 1000
                logger.warn { "429 Too Many Requests (attempt $attempt/$maxRetries), retrying in ${retryAfterMillis}ms" }
                sleep(retryAfterMillis)
                continue
            }
            if (!response.status.isSuccess()) {
                val body = runCatching { response.body<String>() }.getOrDefault("<no body>")
                error("GET $baseUrl failed: ${response.status}\n$body")
            }
            return response.body()
        }
    }

    /** Sleeps just long enough since the last call to keep spacing at [minIntervalMillis]. */
    private suspend fun throttle() {
        val last = lastRequestAtMillis
        if (last != null) {
            val elapsed = now() - last
            if (elapsed < minIntervalMillis) {
                val wait = minIntervalMillis - elapsed
                logger.debug { "throttling for ${wait}ms" }
                sleep(wait)
            }
        }
        lastRequestAtMillis = now()
    }
}
