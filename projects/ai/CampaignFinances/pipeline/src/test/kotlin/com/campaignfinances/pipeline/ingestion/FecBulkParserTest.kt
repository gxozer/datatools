package com.campaignfinances.pipeline.ingestion

import org.junit.jupiter.api.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class FecBulkParserTest {

    private fun ok(type: FecBulkFileType, line: String): List<String?> =
        assertIs<ParseResult.Ok>(FecBulkParser.parse(type, line)).values

    private fun bad(type: FecBulkFileType, line: String): String =
        assertIs<ParseResult.Bad>(FecBulkParser.parse(type, line)).reason

    // --- candidate (cn) ---

    @Test
    fun `parses a candidate line into staging column order`() {
        val values = ok(
            FecBulkFileType.CANDIDATE,
            "H6CA34245|LIEU, TED|DEM|2026|CA|H|36|I|C|C00386987|236 W PORTAL AVE|STE 320|SAN FRANCISCO|CA|94127",
        )
        assertEquals(listOf("H6CA34245", "LIEU, TED", "H", "DEM", "CA", "36"), values)
    }

    @Test
    fun `rejects candidate line with wrong field count`() {
        assertContains(bad(FecBulkFileType.CANDIDATE, "BADROW|ONLY|FIVE|FIELDS|HERE"), "expected 15 fields, got 5")
    }

    @Test
    fun `rejects candidate line with blank CAND_ID`() {
        assertContains(bad(FecBulkFileType.CANDIDATE, " |X|DEM|2026|CA|H|36|I|C|C1|a|b|c|CA|94127"), "missing CAND_ID")
    }

    // --- committee (cm) ---

    @Test
    fun `parses a committee line`() {
        val values = ok(
            FecBulkFileType.COMMITTEE,
            "C00386987|TED LIEU FOR CONGRESS|DOE, JANE|ADDR1|ADDR2|SF|CA|94127|P|H|DEM|Q|||H6CA34245",
        )
        assertEquals(listOf("C00386987", "TED LIEU FOR CONGRESS", "H", "P"), values)
    }

    @Test
    fun `rejects committee line with blank CMTE_ID`() {
        assertContains(bad(FecBulkFileType.COMMITTEE, "|MISSING ID|X|a|b|c|d|e|f|g|h|i|j|k|l"), "missing CMTE_ID")
    }

    // --- linkage (ccl) ---

    @Test
    fun `parses a linkage line`() {
        val values = ok(FecBulkFileType.LINKAGE, "H6CA34245|2026|2026|C00386987|H|P|201602170300018598")
        assertEquals(listOf("H6CA34245", "C00386987", "H", "P", "201602170300"), values)
    }

    @Test
    fun `rejects linkage line with wrong field count`() {
        assertContains(bad(FecBulkFileType.LINKAGE, "SHORT|ROW"), "expected 7 fields, got 2")
    }

    // --- contributions (itcont) ---

    private val goodContribution =
        "C00386987|N|Q1|P|202604159876543210|15|IND|SMITH, JOHN A|LOS ANGELES|CA|900121234|ACME CORP|ENGINEER|03152026|500||SA11AI.1234|1234567|||4041520261234567890"

    @Test
    fun `parses a contribution line into staging column order`() {
        val values = ok(FecBulkFileType.CONTRIBUTIONS, goodContribution)
        assertEquals(
            listOf(
                "4041520261234567890", "C00386987", "SMITH, JOHN A", "LOS ANGELES", "CA",
                "900121234", "ACME CORP", "ENGINEER", "03152026", "500",
            ),
            values,
        )
    }

    @Test
    fun `accepts negative contribution amounts (refunds)`() {
        val line = goodContribution.replace("|500|", "|-250|")
        assertEquals("-250", ok(FecBulkFileType.CONTRIBUTIONS, line).last())
    }

    @Test
    fun `blank transaction date becomes null without rejecting the row`() {
        val line = goodContribution.replace("|03152026|", "||")
        assertNull(ok(FecBulkFileType.CONTRIBUTIONS, line)[8])
    }

    @Test
    fun `rejects contribution with blank SUB_ID`() {
        val line = goodContribution.replace("4041520261234567890", " ")
        assertContains(bad(FecBulkFileType.CONTRIBUTIONS, line), "missing SUB_ID")
    }

    @Test
    fun `rejects contribution with non-numeric amount`() {
        val line = goodContribution.replace("|500|", "|NOTANUMBER|")
        assertContains(bad(FecBulkFileType.CONTRIBUTIONS, line), "invalid TRANSACTION_AMT")
    }

    @Test
    fun `oversized free-text fields are clipped to staging column widths`() {
        val longName = "X".repeat(250)
        val line = goodContribution.replace("SMITH, JOHN A", longName)
        assertEquals(200, ok(FecBulkFileType.CONTRIBUTIONS, line)[2]!!.length)
    }
}
