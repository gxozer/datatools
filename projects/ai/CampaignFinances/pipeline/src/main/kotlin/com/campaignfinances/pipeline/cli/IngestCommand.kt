package com.campaignfinances.pipeline.cli

import com.campaignfinances.pipeline.db.DbConfig
import com.campaignfinances.pipeline.ingestion.BulkIngestRunner
import com.campaignfinances.pipeline.ingestion.FecApiAdapter
import com.campaignfinances.pipeline.ingestion.FecApiConfig
import com.campaignfinances.pipeline.ingestion.FecBulkAdapter
import com.campaignfinances.pipeline.ingestion.FecBulkDownloader
import java.nio.file.Path

/**
 * `ingest` — loads FEC data into the staging schema.
 *
 * Options (all `--key=value` form):
 * - `--source=fec-bulk|fec-api` (required) — which adapter to use.
 * - `--cycle=<year>` (optional, default 2026) — the two-year federal election
 *   cycle to load (e.g. 2026 covers 2025–2026).
 * - `--dir=<path>` (optional, `fec-bulk` only) — read pre-downloaded/extracted
 *   `.txt` files from this directory instead of downloading from fec.gov.
 *   Used by tests and offline runs; point it at
 *   `pipeline/src/test/resources/fixtures/fec-bulk` for a fast small-data run.
 *   Ignored by `fec-api`, which has no local files.
 *
 * @param runners which [BulkIngestRunner] to build for each `--source`
 *   value, keyed by that value. A *supplier* rather than a built instance, so
 *   constructing one source's adapter (e.g. reading `FEC_API_KEY`) only
 *   happens if that source is actually selected — `pipeline ingest
 *   --source=fec-bulk` must not fail just because the API key isn't set.
 * @param out where progress and the final summary are printed
 * @param err where usage errors are printed
 */
class IngestCommand(
    private val runners: Map<String, () -> BulkIngestRunner>,
    private val out: Appendable = System.out,
    private val err: Appendable = System.err,
) : Command {

    /**
     * Production wiring: `fec-bulk` goes through [FecBulkAdapter], `fec-api`
     * through [FecApiAdapter] (reading [FecApiConfig.fromEnv] lazily, only if
     * selected). Tests use the primary constructor with fakes instead.
     */
    constructor(dbConfig: DbConfig, out: Appendable = System.out, err: Appendable = System.err) : this(
        runners = mapOf(
            "fec-bulk" to { FecBulkAdapter(dbConfig, FecBulkDownloader(out = out), out) as BulkIngestRunner },
            "fec-api" to { FecApiAdapter(dbConfig, FecApiConfig.fromEnv(), out = out) as BulkIngestRunner },
        ),
        out = out,
        err = err,
    )

    override val name = "ingest"
    override val description = "Load FEC data into staging and canonical schema (--source=fec-bulk|fec-api [--cycle=2026] [--dir=<path>])"

    /**
     * Parses options, validates them, and dispatches to the selected source.
     * @return 0 on success, 2 on any usage error (bad/missing/unknown option)
     */
    override fun run(args: List<String>): Int {
        val options = parseOptions(args)

        // --cycle: absent → default 2026; present but non-numeric → usage error.
        val rawCycle = options["cycle"]
        val cycle: Int
        if (rawCycle == null) {
            cycle = 2026
        } else {
            val parsed = rawCycle.toIntOrNull()
            if (parsed == null) {
                err.appendLine("invalid --cycle '$rawCycle'")
                return 2
            }
            cycle = parsed
        }

        val localDir = options["dir"]?.let(Path::of)

        val source = options["source"] ?: return usageError("missing required --source=fec-bulk|fec-api")
        val makeRunner = runners[source] ?: return usageError("unknown --source '$source' (expected fec-bulk or fec-api)")
        return runIngest(makeRunner(), cycle, localDir)
    }

    /**
     * Runs the selected source's ingest and prints a one-line summary with
     * the run id and the totals across all of that source's files.
     */
    private fun runIngest(runner: BulkIngestRunner, cycle: Int, localDir: Path?): Int {
        val summary = runner.ingest(cycle, localDir)
        val loaded = summary.files.values.sumOf { it.loaded }
        val bad = summary.files.values.sumOf { it.bad }
        out.appendLine("ingest run ${summary.runId} complete: $loaded rows loaded, $bad bad rows skipped")
        return 0
    }

    /** Prints the message to stderr and returns the usage-error exit code. */
    private fun usageError(message: String): Int {
        err.appendLine(message)
        return 2
    }

    /**
     * Converts `--key=value` arguments to a key/value map.
     * Arguments not in that form (no `--` prefix, or no `=`) are ignored.
     */
    private fun parseOptions(args: List<String>): Map<String, String> {
        val options = mutableMapOf<String, String>()
        for (arg in args) {
            if (!arg.startsWith("--")) continue
            // limit = 2 keeps any '=' inside the value intact (e.g. --dir=/a=b)
            val keyValue = arg.removePrefix("--").split('=', limit = 2)
            if (keyValue.size == 2) {
                options[keyValue[0]] = keyValue[1]
            }
        }
        return options
    }
}
