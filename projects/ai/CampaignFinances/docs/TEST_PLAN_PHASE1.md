# Phase 1 Test Plan — Campaign Finances Data Pipeline

**Epic:** [PR-144](https://mgozer.atlassian.net/browse/PR-144)
**Parent docs:** [TDS_PHASE1.md](TDS_PHASE1.md) §9 (testing strategy, brief), [PROJECT_PLAN.md](PROJECT_PLAN.md)
**Status:** Living document — updated as each Phase 1 ticket lands
**Last updated:** 2026-06-23 (PR-171–PR-174 closed)

This is the operational expansion of [TDS_PHASE1.md](TDS_PHASE1.md) §9: not just the testing *strategy* (unit/integration/reconciliation, stated briefly there), but a concrete inventory of what test coverage exists today, what's planned per ticket, and the gaps tracked between them. Phase 1 is the only phase implemented so far ([PROJECT_PLAN.md](PROJECT_PLAN.md) defines 7 phases total); this document will need a Phase 2 counterpart once that phase is designed.

## 1. Test types used in this project

| Type | Definition | Where it lives here |
|---|---|---|
| **Unit** | Pure functions, no I/O, no database. | `FecBulkParserTest` (parses raw strings, asserts on values — no DB). |
| **Schema/contract** | Asserts the database schema's own guarantees (column nullability, indexes) independent of any insert. | `CanonicalSchemaContractTest` (PR-172) — table-driven nullability assertions for all 4 canonical tables via `information_schema.columns`. |
| **Component** | One class tested against its real dependency (a real DB via Testcontainers), bypassing sibling components. | `CanonicalLoaderTest` (real MySQL, but skips `FecBulkAdapter`/CLI/downloader entirely). `FlywayMigrationTest` (schema only). `IngestCommandComponentTest` (PR-173) — the CLI layer against a real `FecBulkAdapter`, bypassing the fake `BulkIngestRunner` `IngestCommandTest` uses. |
| **Integration** | Multiple real components wired together, exercised through one public entry point, still in-process. | `FecBulkIngestIntegrationTest` (`FecBulkAdapter.ingest()` → `StagingLoader` + `CanonicalLoader`, real MySQL). |
| **End-to-end (in-process)** | Every pipeline stage chained together (ingest → dedup → summary rebuild → reconcile), still calling Kotlin classes in-process. | Not yet present — planned in **PR-159**. |
| **End-to-end (subprocess)** | The actual CLI run as a separate process exactly as a user runs it, real env vars, real exit codes. | `CliSubprocessTest` (PR-174) — spawns `java -cp ... MainKt migrate`/`ingest` as a genuine child process against a real Testcontainers MySQL via `CF_DB_*` env vars. Distinct from PR-159's scope, which stays in-process per its ticket description. |

## 2. Current test inventory (as of PR-174)

| File | Type | Covers |
|---|---|---|
| `cli/CliTest.kt` | Unit | `Cli` dispatch: routes to the named command, usage-on-no-args, unknown-command exit code |
| `cli/StubCommandsTest.kt` | Unit | `DedupCommand`/`ReconcileCommand` stubs exit 2 with "not implemented" — guards against accidentally shipping a stub as if it were real |
| `cli/IngestCommandTest.kt` | Unit (fake collaborator) | `IngestCommand` option parsing (`--source`, `--cycle`, `--dir`) against a **fake** `BulkIngestRunner` — no real DB |
| `cli/IngestCommandComponentTest.kt` | Component | The production `IngestCommand(dbConfig)` constructor against a real `FecBulkAdapter` + Testcontainers MySQL: real exit code, real printed summary line, real fixture-derived counts (PR-173) |
| `db/DbConfigTest.kt` | Unit | `DbConfig.fromEnv()` defaults and env-var overrides |
| `FlywayMigrationTest.kt` | Component | All migrations (V1–V12) apply cleanly to an empty DB; all 11 expected tables exist afterward |
| `CliSubprocessTest.kt` | End-to-end (subprocess) | The built CLI run as a genuine child process (`migrate` then `ingest`) against real `CF_DB_*` env vars and a real Testcontainers MySQL (PR-174) |
| `ingestion/FecBulkParserTest.kt` | Unit | Pure parsing logic for all 4 FEC bulk file types — good rows, malformed rows, negative amounts, oversized fields, blank dates |
| `ingestion/CanonicalLoaderTest.kt` | Component | `CanonicalLoader`'s 4 upsert methods in isolation: idempotency (same run, re-invoked), **cross-run upserts with a corrected value (different `ingest_run_id`, PR-171)**, attribution non-filtering, unresolved-FK and blank-designation skip counting, donor_id preservation, NOT-NULL skip counting, affected-rows assumption, table-driven V12 generated-column normalization including a blank `contributor_name` |
| `ingestion/CanonicalSchemaContractTest.kt` | Schema/contract | Table-driven `information_schema.columns` nullability assertions for every column on `candidate`, `committee`, `candidate_committee`, `contribution` (PR-172) |
| `ingestion/FecBulkIngestIntegrationTest.kt` | Integration | Full `FecBulkAdapter.ingest()` against fixture files: staging load + canonical load + `ingest_run.row_counts`, end-to-end idempotency on re-run |
| `ingestion/TestDbSupport.kt` | (test helper, not a test) | Shared `Connection.truncateAllPipelineTables()` / `queryLong()` / `queryString()` used by the Testcontainers-backed tests above |

## 3. Coverage by Phase 1 ticket

| Ticket | Status | Test scope (per ticket's own AC) | Current automated coverage |
|---|---|---|---|
| PR-152 scaffolding | Done | CLI dispatch, `DbConfig`, stub commands, Flyway migration | `CliTest`, `StubCommandsTest`, `DbConfigTest`, `FlywayMigrationTest` — complete |
| PR-153 bulk adapter | Done | Parser unit tests (all 4 file types, malformed rows); staging-load integration test | `FecBulkParserTest`, `FecBulkIngestIntegrationTest` (staging half) — complete |
| **PR-154 normalize/load** | Done | Table-driven normalization + attribution-logic unit tests; canonical population + idempotency | `CanonicalLoaderTest`, `FecBulkIngestIntegrationTest` (canonical half) — complete; follow-up gaps from review closed via PR-171–PR-174 (§4) |
| PR-155 FEC API adapter | Not started | Mocked-HTTP unit tests (response parsing, pagination, watermark logic), cross-source idempotency integration test | None yet — ticket not started |
| PR-156 donor dedup | Not started | Table-driven normalization rules **and near-miss cases that must NOT merge**; audit-completeness check (`donor_link` count = linked-contribution count); determinism (re-run = identical) | None yet — depends on PR-154's `normalized_name`/`zip5` columns, which exist |
| PR-157 ranking tables | Not started | Ranking/breakdown integration tests; concurrent-read-during-rebuild test (atomic `RENAME TABLE` swap) | None yet |
| PR-158 reconciliation | Not started | Comparison/tolerance unit tests; fixture-based reconciliation test (known inputs → known results) | None yet |
| **PR-159 e2e suite** | Not started | Cross-stage end-to-end (ingest → dedup → summary rebuild) against curated fixtures with edge cases (malformed rows, dedup near-misses, multi-committee candidates); e2e idempotency (full pipeline twice = identical state) | None yet — explicitly **in-process** per its ticket description, not a subprocess/CLI test (see §4.7) |
| PR-160 runbook + demo | Not started | Manual: a fresh-checkout developer follows the runbook with no tribal knowledge | N/A — manual acceptance test, not automated |

## 4. Gaps found during PR-154 review — closed via PR-171–PR-174

### [PR-171](https://mgozer.atlassian.net/browse/PR-171): Cross-run upsert + edge-case test coverage for `CanonicalLoader` — closed
All "idempotent on re-run" tests originally called the same loader method twice with the **same** `ingest_run_id` and **identical** staging data — that proves "calling it twice in a row is safe" but not the real-world shape: two genuinely separate `ingest()` runs (different `ingest_run_id`s) converging on the same canonical row, including when a value **changed** between them (FEC republishing a corrected candidate name or contribution amount).

**Writing that test surfaced a real production bug**, not just a coverage gap: `loadCandidates` and `loadContributions` derived `bad = total - loaded`, where `loaded` came from `INSERT ... ON DUPLICATE KEY UPDATE`'s own affected-rows return value. MySQL reports 1 per inserted row, 1 per matched-unchanged row (Connector/J's "found rows" default), but **2** per matched row whose value actually changed — so that subtraction silently broke (inflated `loaded`, negative `bad`) the instant a re-run's data differed from what was already loaded, which is exactly the scenario FEC corrections produce routinely. `loadCommittees`' reported `loaded` count had the same flaw (its `bad` stayed correctly 0 only because that path hardcodes it). Fixed in `CanonicalLoader.kt` by deriving `loaded`/`bad` from `COUNT(*)` queries instead of the affected-rows value — see the class KDoc there for the full explanation. The two edge-case tests bundled into this ticket (blank `cmte_dsgn`, NULL `contributor_name`) found no further bugs, just closed coverage.

### [PR-172](https://mgozer.atlassian.net/browse/PR-172): Schema/contract test — closed
`CanonicalSchemaContractTest` now asserts canonical-table column nullability directly via `information_schema.columns`, independent of any insert — the kind of test that would have caught the PR-154 candidate-name bug before real data was needed to surface it.

### [PR-173](https://mgozer.atlassian.net/browse/PR-173): `IngestCommand` wired to the real `FecBulkAdapter` — closed
`IngestCommandComponentTest` wires the production `IngestCommand(dbConfig)` constructor to a real Testcontainers MySQL and fixture files, asserting the CLI layer's real exit code and printed summary line.

### [PR-174](https://mgozer.atlassian.net/browse/PR-174): True end-to-end subprocess test — closed
`CliSubprocessTest` spawns the built CLI as a genuine `java` child process (`migrate` then `ingest`) with real `CF_DB_*` env vars against a real Testcontainers MySQL — the one tier that exercises `Main.kt`'s `exitProcess()` and real env-var wiring, distinct from PR-159's in-process cross-stage e2e suite.

### Deliberately not planned
- **Live volume/timing run** (50–80M real rows, <2h target per [TDS_PHASE1.md](TDS_PHASE1.md) §7) — too slow/heavy to run on every change; stays a manual verification step (see PR-154/PR-160 verification steps), not part of the automated suite.
- **Forcing a genuine mid-canonical-load `SQLException`** to test `FecBulkAdapter`'s FAILED-run path end-to-end — investigated during PR-154; constructing a deterministic failure that survives staging-load but breaks canonical-load is fragile with current constraints, and would require adding a test-only seam into production code (injecting a failing `CanonicalLoader`) for the sake of one test. Flagged as a known gap rather than building speculative test infrastructure for it.

## 5. Running the suite

```
./gradlew test
```

Provisions its own throwaway Testcontainers MySQL — no local Docker Compose setup required to run tests (Docker daemon itself must be running). Full suite currently runs in well under a minute. A file-watcher (see project dev workflow) re-runs this automatically on every source change during active development.

## 6. Tooling notes for contributors

- **Shared Testcontainers instances are not reset between test methods** in the same class (`@Container @JvmStatic` starts once per class, not per test) — every Testcontainers-backed test class must call `Connection.truncateAllPipelineTables()` (see `TestDbSupport.kt`) in `@BeforeEach`, or risk silent cross-test state leakage turning idempotent upserts into false-negative no-ops.
- **`ON DUPLICATE KEY UPDATE` affected-rows counting** depends on MySQL Connector/J's default `useAffectedRows=false` ("found rows" mode) — `CanonicalLoaderTest`'s `loadCandidates reports an unchanged row as loaded, not bad, on re-run` test pins this; if it ever fails, check the JDBC URL/driver version first, not the upsert SQL.
- **Never derive a row count from that same affected-rows value.** It reports 1 for an inserted row, 1 for a matched-and-unchanged row, but **2** for a matched row whose value actually changed — there is no way to recover "how many rows were touched" from the sum once any row in the batch changed. `CanonicalLoader` learned this the hard way (PR-171); every `loaded`/`bad` count there now comes from a `COUNT(*)` query instead.
