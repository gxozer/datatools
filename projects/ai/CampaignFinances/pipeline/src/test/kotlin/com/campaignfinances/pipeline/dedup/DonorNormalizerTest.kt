package com.campaignfinances.pipeline.dedup

import com.campaignfinances.pipeline.dedup.DonorNormalizer.ParsedName
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullAndEmptySource
import org.junit.jupiter.params.provider.ValueSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Table-driven unit tests for [DonorNormalizer]: every normalization rule,
 * plus near-miss cases that must produce different keys (PR-156,
 * docs/TDS_PHASE1.md §5 step 1).
 */
class DonorNormalizerTest {

    // ── parseName ──────────────────────────────────────────────────────────────

    @Test
    fun `parseName splits LAST, FIRST MIDDLE and discards middle name`() {
        assertEquals(ParsedName("SMITH", "JOHN"), DonorNormalizer.parseName("SMITH, JOHN PAUL"))
    }

    @Test
    fun `parseName splits LAST, FIRST with no middle`() {
        assertEquals(ParsedName("DOE", "JANE"), DonorNormalizer.parseName("DOE, JANE"))
    }

    @Test
    fun `parseName with no comma treats entire value as last name`() {
        assertEquals(ParsedName("SMITH", ""), DonorNormalizer.parseName("SMITH"))
    }

    @Test
    fun `parseName strips JR suffix from last name`() {
        assertEquals(ParsedName("SMITH", "JOHN"), DonorNormalizer.parseName("SMITH JR, JOHN"))
    }

    @Test
    fun `parseName strips SR suffix from last name`() {
        assertEquals(ParsedName("JONES", "BOB"), DonorNormalizer.parseName("JONES SR, BOB"))
    }

    @Test
    fun `parseName strips II suffix from last name`() {
        assertEquals(ParsedName("SMITH", "JOHN"), DonorNormalizer.parseName("SMITH II, JOHN"))
    }

    @Test
    fun `parseName strips III suffix from last name`() {
        assertEquals(ParsedName("SMITH", "JOHN"), DonorNormalizer.parseName("SMITH III, JOHN"))
    }

    @Test
    fun `parseName strips IV suffix from last name`() {
        assertEquals(ParsedName("SMITH", "JOHN"), DonorNormalizer.parseName("SMITH IV, JOHN"))
    }

    @Test
    fun `parseName strips ESQ suffix from last name`() {
        assertEquals(ParsedName("SMITH", "JOHN"), DonorNormalizer.parseName("SMITH ESQ, JOHN"))
    }

    @Test
    fun `parseName replaces apostrophe with space in name parts`() {
        // Punctuation is replaced with a space, not removed — "O'BRIEN" → "O BRIEN".
        // Conservative: "OBRIEN" and "O'BRIEN" stay separate rather than risk false merges.
        assertEquals(ParsedName("O BRIEN", "MARY"), DonorNormalizer.parseName("O'BRIEN, MARY"))
    }

    @Test
    fun `parseName replaces hyphen with space in hyphenated last names`() {
        // Hyphenated names become two tokens — conservative: "SMITH-JONES" ≠ "SMITHJONES".
        assertEquals(ParsedName("SMITH JONES", "ANN"), DonorNormalizer.parseName("SMITH-JONES, ANN"))
    }

    @Test
    fun `parseName lowercases input before normalizing`() {
        assertEquals(ParsedName("SMITH", "JOHN"), DonorNormalizer.parseName("smith, john"))
    }

    @Test
    fun `parseName collapses extra whitespace`() {
        assertEquals(ParsedName("SMITH", "JOHN"), DonorNormalizer.parseName("  SMITH  ,  JOHN  "))
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["   "])
    fun `parseName returns null for blank input`(raw: String?) {
        assertNull(DonorNormalizer.parseName(raw))
    }

    @Test
    fun `parseName returns null when last name is all punctuation`() {
        assertNull(DonorNormalizer.parseName("---, JOHN"))
    }

    @Test
    fun `parseName does not skip contributor whose last name equals a suffix token`() {
        // "SR" is in NAME_SUFFIXES, but if it is the ONLY last-name token it must be
        // kept — the contributor should not be silently excluded (PR-204).
        assertEquals(ParsedName("SR", "JOHN"), DonorNormalizer.parseName("SR, JOHN"))
    }

    // ── Near-miss: must NOT produce the same ParsedName ─────────────────────

    @Test
    fun `parseName different last names produce different keys`() {
        val a = DonorNormalizer.parseName("SMITH, JOHN")
        val b = DonorNormalizer.parseName("JONES, JOHN")
        assert(a != b) { "Different last names must not match" }
    }

    @Test
    fun `parseName different first names produce different keys`() {
        val a = DonorNormalizer.parseName("SMITH, JOHN")
        val b = DonorNormalizer.parseName("SMITH, JANE")
        assert(a != b) { "Different first names must not match" }
    }

    // ── normalizeEmployer ─────────────────────────────────────────────────────

    @ParameterizedTest
    @CsvSource(
        "SELF,            SELF EMPLOYED",
        "SELF-EMPLOYED,   SELF EMPLOYED",
        "SELF EMPLOYED,   SELF EMPLOYED",
        "SELFEMPLOYED,    SELF EMPLOYED",
        "self-employed,   SELF EMPLOYED",
        "NOT EMPLOYED,    NOT EMPLOYED",
        "NOT-EMPLOYED,    NOT EMPLOYED",
        "UNEMPLOYED,      NOT EMPLOYED",
    )
    fun `normalizeEmployer maps aliases to canonical forms`(raw: String, expected: String) {
        assertEquals(expected.trim(), DonorNormalizer.normalizeEmployer(raw))
    }

    @ParameterizedTest
    @CsvSource("N/A", "NA", "N.A.", "NONE", "UNKNOWN")
    fun `normalizeEmployer maps not-applicable values to null`(raw: String) {
        assertNull(DonorNormalizer.normalizeEmployer(raw))
    }

    @Test
    fun `normalizeEmployer uppercases and collapses whitespace on a plain employer`() {
        assertEquals("ACME CORP", DonorNormalizer.normalizeEmployer("  acme  corp  "))
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["   "])
    fun `normalizeEmployer returns null for blank input`(raw: String?) {
        assertNull(DonorNormalizer.normalizeEmployer(raw))
    }

    // ── normalizeZip ──────────────────────────────────────────────────────────

    @Test
    fun `normalizeZip extracts first 5 digits from 9-digit zip`() {
        assertEquals("90212", DonorNormalizer.normalizeZip("902121234"))
    }

    @Test
    fun `normalizeZip returns exact value for 5-digit zip`() {
        assertEquals("90212", DonorNormalizer.normalizeZip("90212"))
    }

    @Test
    fun `normalizeZip handles zip with hyphen separator`() {
        assertEquals("90212", DonorNormalizer.normalizeZip("90212-1234"))
    }

    @Test
    fun `normalizeZip returns null when fewer than 5 digits`() {
        assertNull(DonorNormalizer.normalizeZip("9021"))
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = ["   "])
    fun `normalizeZip returns null for blank input`(raw: String?) {
        assertNull(DonorNormalizer.normalizeZip(raw))
    }

    // ── Match-key near-miss cases (must NOT produce the same DonorKey) ───────

    @Test
    fun `same name different zip5 must not merge`() {
        val zip1 = DonorNormalizer.normalizeZip("90210")
        val zip2 = DonorNormalizer.normalizeZip("10001")
        assert(zip1 != zip2) { "Different zip5 values must not merge" }
    }

    @Test
    fun `same name same zip different non-blank employers must not merge`() {
        val e1 = DonorNormalizer.normalizeEmployer("ACME CORP")
        val e2 = DonorNormalizer.normalizeEmployer("BIGCORP")
        assert(e1 != e2) { "Different employers must not merge" }
    }

    @Test
    fun `blank employer and named employer produce different normalized values`() {
        val blank = DonorNormalizer.normalizeEmployer(null)   // null
        val named = DonorNormalizer.normalizeEmployer("ACME") // "ACME"
        assert(blank != named) { "Blank and named employer must not merge" }
    }
}
