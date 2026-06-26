package com.campaignfinances.pipeline.ingestion

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One page of `GET /v1/schedules/schedule_a/` (docs/TDS_PHASE1.md §3). */
@Serializable
data class ScheduleAResponse(
    val results: List<ScheduleAResult>,
    val pagination: Pagination,
)

/**
 * The subset of FEC's schedule_a (itemized individual contribution) fields
 * this adapter needs — the same data `staging_contribution` already holds
 * for the bulk file's `indiv`/`itcont.txt` rows ([FecBulkFileType.CONTRIBUTIONS]),
 * just fetched from the API instead of a pipe-delimited bulk file.
 */
@Serializable
data class ScheduleAResult(
    @SerialName("sub_id") val subId: Long,
    @SerialName("committee_id") val committeeId: String,
    @SerialName("contributor_name") val contributorName: String? = null,
    @SerialName("contributor_city") val contributorCity: String? = null,
    @SerialName("contributor_state") val contributorState: String? = null,
    @SerialName("contributor_zip") val contributorZip: String? = null,
    @SerialName("contributor_employer") val contributorEmployer: String? = null,
    @SerialName("contributor_occupation") val contributorOccupation: String? = null,
    @SerialName("contribution_receipt_date") val contributionReceiptDate: String? = null,
    @SerialName("contribution_receipt_amount") val contributionReceiptAmount: Double? = null,
)

/**
 * @property page the current 1-based page number, or 0 if the API omits it
 *   (the real schedule_a endpoint omits `page` when using keyset pagination)
 * @property pages total pages available for this query
 * @property lastIndexes the keyset cursor for requesting the next page; null
 *   signals the last page — this is the authoritative termination signal, not
 *   `page >= pages`
 */
@Serializable
data class Pagination(
    val page: Int = 0,
    val pages: Int,
    @SerialName("last_indexes") val lastIndexes: LastIndexes? = null,
)

/** FEC's keyset pagination cursor — pass both fields back to get the next page. */
@Serializable
data class LastIndexes(
    @SerialName("last_index") val lastIndex: String,
    @SerialName("last_contribution_receipt_date") val lastContributionReceiptDate: String,
)
