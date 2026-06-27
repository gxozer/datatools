package com.campaignfinances.pipeline.dedup

/**
 * Pure functions for normalizing donor identity fields before de-duplication
 * matching (PR-156, docs/TDS_PHASE1.md §5 step 1).
 *
 * All functions are stateless and side-effect-free so they can be
 * exhaustively unit-tested in isolation without a database.
 */
object DonorNormalizer {

    /**
     * A donor name split into its normalized last and first components.
     *
     * @property lastName normalized last name — uppercase, no punctuation, no suffixes
     * @property firstName normalized first name (first token only; middle names ignored)
     */
    data class ParsedName(val lastName: String, val firstName: String)

    /**
     * Honorific/generation suffixes that are stripped before name comparison.
     * FEC data includes these as part of the name field.
     */
    private val NAME_SUFFIXES = setOf("JR", "SR", "II", "III", "IV", "ESQ", "MD", "PHD", "DDS", "RET")

    /**
     * Maps raw employer values that mean "self-employed" or "not applicable"
     * to their canonical forms. Null means "treat as blank" (no employer).
     */
    private val EMPLOYER_CANONICAL: Map<String, String?> = mapOf(
        "SELF" to "SELF EMPLOYED",
        "SELF-EMPLOYED" to "SELF EMPLOYED",
        "SELF EMPLOYED" to "SELF EMPLOYED",
        "SELFEMPLOYED" to "SELF EMPLOYED",
        "NOT EMPLOYED" to "NOT EMPLOYED",
        "NOT-EMPLOYED" to "NOT EMPLOYED",
        "UNEMPLOYED" to "NOT EMPLOYED",
        "N/A" to null,
        "NA" to null,
        "N.A." to null,
        "NONE" to null,
        "UNKNOWN" to null,
        "INFORMATION REQUESTED" to null,
        "INFORMATION REQUESTED PER BEST EFFORTS" to null,
    )

    /**
     * Splits a raw FEC contributor name into normalized last and first components.
     *
     * FEC bulk files store names as `"LAST, FIRST MIDDLE"` (or `"LAST, FIRST"`).
     * This function:
     * 1. Uppercases and trims the raw value.
     * 2. Splits on the first comma — everything before is last name, after is first.
     * 3. Calls [normalizeNamePart] on each component (strips punctuation + suffixes).
     * 4. Takes only the first token of the first-name component (ignores middle).
     *
     * If there is no comma the entire value is treated as the last name.
     *
     * @param raw the raw contributor_name field from staging_contribution
     * @return the parsed name, or null if [raw] is blank or the last name normalizes to empty
     */
    fun parseName(raw: String?): ParsedName? {
        if (raw.isNullOrBlank()) return null
        val upper = raw.uppercase().trim()
        val commaIndex = upper.indexOf(',')
        val rawLast = if (commaIndex >= 0) upper.substring(0, commaIndex) else upper
        val rawFirst = if (commaIndex >= 0) upper.substring(commaIndex + 1) else ""
        val lastName = normalizeNamePart(rawLast)
        if (lastName.isEmpty()) return null
        val firstName = firstToken(normalizeNamePart(rawFirst))
        return ParsedName(lastName, firstName)
    }

    /**
     * Normalizes one component of a name (last or first):
     * strips non-letter characters, collapses whitespace, removes [NAME_SUFFIXES].
     *
     * Input is expected to be uppercase already (from [parseName]).
     *
     * @param raw the raw name component, already uppercased
     * @return the normalized component; empty string if only punctuation or suffixes remain
     */
    fun normalizeNamePart(raw: String): String {
        val upper = raw.uppercase()
        val lettersAndSpaces = upper.replace(Regex("[^A-Z ]"), " ")
        val collapsed = lettersAndSpaces.trim().replace(Regex("\\s+"), " ")
        val tokens = collapsed.split(" ").filter { token -> token.isNotEmpty() && token !in NAME_SUFFIXES }
        return tokens.joinToString(" ")
    }

    /**
     * Normalizes an employer field per TDS §5 step 1: uppercase, trim, collapse
     * whitespace, then resolve known aliases via [EMPLOYER_CANONICAL].
     *
     * @param raw the raw employer field from staging_contribution
     * @return the normalized employer string, or null if blank or a "not applicable" alias
     */
    fun normalizeEmployer(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val upper = raw.uppercase().trim().replace(Regex("\\s+"), " ")
        if (EMPLOYER_CANONICAL.containsKey(upper)) return EMPLOYER_CANONICAL[upper]
        return upper
    }

    /**
     * Extracts the 5-digit zip code prefix from a raw zip field.
     *
     * FEC bulk zip fields may be `"902121234"` (9-digit) or `"90212"` (5-digit)
     * or padded/formatted variants. This extracts the first 5 digit characters.
     *
     * @param raw the raw zip_code field from staging_contribution
     * @return the 5-digit zip5 string, or null if [raw] has fewer than 5 digits
     */
    fun normalizeZip(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter(Char::isDigit)
        return if (digits.length >= 5) digits.substring(0, 5) else null
    }

    /**
     * Returns the first whitespace-delimited token of [text], used to extract
     * just the first name from a "FIRST MIDDLE" string.
     *
     * @param text a space-separated, already-normalized string
     * @return the first token, or the whole string if there is no space
     */
    private fun firstToken(text: String): String {
        val spaceIndex = text.indexOf(' ')
        return if (spaceIndex < 0) text else text.substring(0, spaceIndex)
    }
}
