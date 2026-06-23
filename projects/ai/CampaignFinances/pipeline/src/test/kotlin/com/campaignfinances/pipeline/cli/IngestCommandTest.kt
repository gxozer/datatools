package com.campaignfinances.pipeline.cli

import com.campaignfinances.pipeline.ingestion.BulkIngestRunner
import com.campaignfinances.pipeline.ingestion.FileCounts
import com.campaignfinances.pipeline.ingestion.IngestSummary
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IngestCommandTest {

    private fun command(
        out: StringBuilder = StringBuilder(),
        err: StringBuilder = StringBuilder(),
        onBulkIngest: BulkIngestRunner = BulkIngestRunner { _, _ -> IngestSummary(1, emptyMap()) },
        onApiIngest: BulkIngestRunner = BulkIngestRunner { _, _ -> IngestSummary(1, emptyMap()) },
    ) = IngestCommand(mapOf("fec-bulk" to { onBulkIngest }, "fec-api" to { onApiIngest }), out, err)

    @Test
    fun `fec-bulk runs the bulk ingest with default cycle and prints a summary`() {
        var seenCycle: Int? = null
        var seenDir: Path? = Path.of("sentinel")
        val out = StringBuilder()
        val cmd = command(out = out, onBulkIngest = BulkIngestRunner { cycle, dir ->
            seenCycle = cycle
            seenDir = dir
            IngestSummary(7, mapOf("cn" to FileCounts(3, 1)))
        })

        val exitCode = cmd.run(listOf("--source=fec-bulk"))

        assertEquals(0, exitCode)
        assertEquals(2026, seenCycle)
        assertNull(seenDir)
        assertContains(out.toString(), "ingest run 7 complete: 3 rows loaded, 1 bad rows skipped")
    }

    @Test
    fun `cycle and dir options are passed through`() {
        var seen: Pair<Int, Path?>? = null
        val cmd = command(onBulkIngest = BulkIngestRunner { cycle, dir ->
            seen = cycle to dir
            IngestSummary(1, emptyMap())
        })

        assertEquals(0, cmd.run(listOf("--source=fec-bulk", "--cycle=2024", "--dir=/tmp/fec")))
        assertEquals(2024 to Path.of("/tmp/fec"), seen)
    }

    @Test
    fun `missing source exits 2`() {
        val err = StringBuilder()
        assertEquals(2, command(err = err).run(emptyList()))
        assertContains(err.toString(), "missing required --source")
    }

    @Test
    fun `unknown source exits 2`() {
        val err = StringBuilder()
        assertEquals(2, command(err = err).run(listOf("--source=ftp")))
        assertContains(err.toString(), "unknown --source 'ftp'")
    }

    @Test
    fun `fec-api dispatches to its own runner, independent of fec-bulk`() {
        var bulkCalled = false
        var apiSeenCycle: Int? = null
        val out = StringBuilder()
        val cmd = command(
            out = out,
            onBulkIngest = BulkIngestRunner { _, _ -> bulkCalled = true; IngestSummary(1, emptyMap()) },
            onApiIngest = BulkIngestRunner { cycle, _ ->
                apiSeenCycle = cycle
                IngestSummary(9, mapOf("indiv" to FileCounts(5, 0)))
            },
        )

        val exitCode = cmd.run(listOf("--source=fec-api", "--cycle=2026"))

        assertEquals(0, exitCode)
        assertEquals(2026, apiSeenCycle)
        assertEquals(false, bulkCalled, "fec-api must not invoke the fec-bulk runner")
        assertContains(out.toString(), "ingest run 9 complete: 5 rows loaded, 0 bad rows skipped")
    }

    @Test
    fun `invalid cycle exits 2`() {
        val err = StringBuilder()
        assertEquals(2, command(err = err).run(listOf("--source=fec-bulk", "--cycle=abc")))
        assertContains(err.toString(), "invalid --cycle")
    }
}
