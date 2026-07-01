package com.campaignfinances.pipeline.ingestion

/**
 * Result of parsing one line of an FEC bulk file.
 *
 * A sealed interface so callers must handle both cases — there is no way to
 * "forget" about malformed rows.
 */
sealed interface ParseResult {

    /**
     * The line parsed successfully.
     * @property values column values ordered to match
     *   [FecBulkFileType.stagingColumns]; null means SQL NULL
     */
    data class Ok(val values: List<String?>) : ParseResult

    /**
     * The line is malformed and must be skipped (counted, never fatal).
     * @property reason human-readable explanation for the log
     */
    data class Bad(val reason: String) : ParseResult
}

/**
 * Pure parsing/validation of FEC pipe-delimited bulk lines into staging rows.
 *
 * Design rules (per docs/TDS_PHASE1.md):
 * - **Pure functions** — no I/O, no state; everything is unit-testable with
 *   plain strings (see FecBulkParserTest).
 * - **Conservative** — rows missing required identifiers (candidate id,
 *   committee id, SUB_ID) or with unparseable amounts are rejected as [ParseResult.Bad].
 * - **Tolerant where safe** — a malformed *date* nulls the field rather than
 *   rejecting the row; oversized free-text is clipped to the staging column
 *   width so `LOAD DATA` never fails on length in strict mode.
 *
 * Field indices below follow the official FEC file descriptions
 * (https://www.fec.gov/campaign-finance-data/ → file descriptions per type).
 */
object FecBulkParser {

    /**
     * Parses one line of the given file type.
     *
     * @param type which FEC file the line came from (decides layout + validation)
     * @param line one raw pipe-delimited line, without the trailing newline
     * @return [ParseResult.Ok] with staging-ordered values, or [ParseResult.Bad]
     */
    fun parse(type: FecBulkFileType, line: String): ParseResult {
        val fields = line.split('|')
        if (fields.size != type.expectedFields) {
            return ParseResult.Bad("expected ${type.expectedFields} fields, got ${fields.size}")
        }
        return when (type) {
            FecBulkFileType.CANDIDATE -> parseCandidate(fields)
            FecBulkFileType.COMMITTEE -> parseCommittee(fields)
            FecBulkFileType.LINKAGE -> parseLinkage(fields)
            FecBulkFileType.CONTRIBUTIONS -> parseContribution(fields)
        }
    }

    /**
     * Candidate master (cn.txt). Fields used:
     * 0=CAND_ID (required), 1=CAND_NAME, 2=CAND_PTY_AFFILIATION,
     * 4=CAND_OFFICE_ST, 5=CAND_OFFICE, 6=CAND_OFFICE_DISTRICT.
     */
    private fun parseCandidate(fields: List<String>): ParseResult {
        val candId = fields[0].trim()
        if (candId.isEmpty()) return ParseResult.Bad("missing CAND_ID")

        return ParseResult.Ok(
            listOf(
                candId.take(9),
                fields[1].clip(200),    // CAND_NAME
                fields[5].clip(1),      // CAND_OFFICE (P/S/H)
                fields[2].clip(3),      // CAND_PTY_AFFILIATION
                fields[4].clip(2),      // CAND_OFFICE_ST
                fields[6].clip(2),      // CAND_OFFICE_DISTRICT
            ),
        )
    }

    /**
     * Committee master (cm.txt). Fields used:
     * 0=CMTE_ID (required), 1=CMTE_NM, 8=CMTE_DSGN, 9=CMTE_TP.
     */
    private fun parseCommittee(fields: List<String>): ParseResult {
        val cmteId = fields[0].trim()
        if (cmteId.isEmpty()) return ParseResult.Bad("missing CMTE_ID")

        return ParseResult.Ok(
            listOf(
                cmteId.take(9),
                fields[1].clip(200),    // CMTE_NM
                fields[9].clip(1),      // CMTE_TP
                fields[8].clip(1),      // CMTE_DSGN
            ),
        )
    }

    /**
     * Candidate–committee linkage (ccl.txt). Fields used:
     * 0=CAND_ID (required), 3=CMTE_ID (required), 4=CMTE_TP, 5=CMTE_DSGN,
     * 6=LINKAGE_ID. Linkage type P (principal campaign committee) is what
     * rankings traverse, per the attribution decision on PR-144.
     */
    private fun parseLinkage(fields: List<String>): ParseResult {
        val candId = fields[0].trim()
        if (candId.isEmpty()) return ParseResult.Bad("missing CAND_ID")
        val cmteId = fields[3].trim()
        if (cmteId.isEmpty()) return ParseResult.Bad("missing CMTE_ID")

        return ParseResult.Ok(
            listOf(
                candId.take(9),
                cmteId.take(9),
                fields[4].clip(1),      // CMTE_TP
                fields[5].clip(1),      // CMTE_DSGN
                fields[6].clip(12),     // LINKAGE_ID
            ),
        )
    }

    /**
     * Itemized individual contributions (itcont.txt). Fields used:
     * 0=CMTE_ID (required), 7=NAME, 8=CITY, 9=STATE, 10=ZIP_CODE, 11=EMPLOYER,
     * 12=OCCUPATION, 13=TRANSACTION_DT (MMDDYYYY), 14=TRANSACTION_AMT,
     * 20=SUB_ID (required — it is the idempotency key `source_record_id`).
     *
     * Amounts may be negative (refunds). A date that is not exactly 8 digits
     * becomes NULL instead of rejecting the row.
     */
    private fun parseContribution(fields: List<String>): ParseResult {
        val subId = fields[20].trim()
        if (subId.isEmpty()) return ParseResult.Bad("missing SUB_ID")
        val cmteId = fields[0].trim()
        if (cmteId.isEmpty()) return ParseResult.Bad("missing CMTE_ID")
        val amount = fields[14].trim().toBigDecimalOrNull()
            ?: return ParseResult.Bad("invalid TRANSACTION_AMT '${fields[14].trim()}'")

        val date = parseDateOrNull(fields[13])

        return ParseResult.Ok(
            listOf(
                subId.take(19),
                cmteId.take(9),
                fields[7].clip(200),    // NAME
                fields[8].clip(30),     // CITY
                fields[9].clip(2),      // STATE
                fields[10].clip(9),     // ZIP_CODE
                fields[11].clip(38),    // EMPLOYER
                fields[12].clip(38),    // OCCUPATION
                date,                   // TRANSACTION_DT (MMDDYYYY or null)
                amount.toPlainString(),
            ),
        )
    }

    /**
     * Returns [raw] trimmed if it is exactly 8 ASCII digits (the `MMDDYYYY`
     * format FEC bulk files use), or null if the field is absent or malformed.
     * A null date is stored as SQL NULL rather than rejecting the row.
     *
     * @param raw the raw field value from the pipe-delimited line
     * @return the trimmed date string, or null if it is not a valid date field
     */
    private fun parseDateOrNull(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.length == 8 && trimmed.all(Char::isDigit)) return trimmed
        return null
    }

    /**
     * Trims, truncates to the staging column width, and converts blank to null
     * (so empty FEC fields become SQL NULL rather than empty strings).
     */
    private fun String.clip(max: Int): String? = trim().take(max).ifEmpty { null }
}
