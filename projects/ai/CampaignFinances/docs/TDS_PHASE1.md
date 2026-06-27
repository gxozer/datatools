# Phase 1: Data Pipeline Proof — Technical Design Specification

**Epic:** [PR-144](https://mgozer.atlassian.net/browse/PR-144)
**Parent docs:** [PRD_PHASE1.md](PRD_PHASE1.md), [PROJECT_PLAN.md](PROJECT_PLAN.md)
**Status:** Draft
**Last updated:** 2026-06-26 (PR-156 dedup implementation complete)

## 1. Overview

A Kotlin batch pipeline that ingests FEC data for the 2025–2026 cycle into MySQL, normalizes it into a source-agnostic schema, de-duplicates donors, and produces ranked totals with a reconciliation report. Runs manually via CLI in Phase 1; Phase 4 wraps the same entry point in a scheduler.

## 2. Technology Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Language | Kotlin (JVM 21) | Per PR-140; pipeline shares models with the Phase 2 API |
| Build | Gradle (Kotlin DSL) | Standard for Kotlin |
| Database | MySQL 8.4 (LTS) | Product-owner decision (2026-06-11). Handles tens of millions of contribution rows; window functions (8.0+) cover ranking queries; runs identically local (Docker) and hosted. No materialized views — rankings use pipeline-refreshed summary tables instead |
| Migrations | Flyway | Versioned schema, repeatable from empty DB. Note: MySQL DDL is implicit-commit (not transactional), so each migration is kept small and single-statement where possible |
| DB access | JDBC + jOOQ | Type-safe SQL for bulk-heavy workloads; avoids ORM overhead on 10M+ row inserts; full MySQL dialect support |
| HTTP client | Ktor client | For api.open.fec.gov; Kotlin-native, easy rate-limit handling |
| Logging | SLF4J + Logback + kotlin-logging | Standard JVM logging; level controlled by `CF_LOG_LEVEL` env var (default INFO). `DEBUG` logs full request URLs, watermark resolution, throttle waits, and retry attempts |
| Testing | JUnit 5 + Testcontainers (MySQL) | Integration tests against a real MySQL, fixture FEC files in `src/test/resources` |
| Runtime | CLI (`./gradlew :pipeline:run --args="..."` from the project root) + Docker Compose for MySQL | No scheduler in Phase 1 |

## 3. Architecture

```
                 ┌─────────────────────────────────────────────────────┐
                 │                     Pipeline CLI                      │
                 │  ingest --source=fec-bulk | fec-api  [--cycle] [--dir]│
                 └─────────────────────────────────────────────────────┘
                          │                          │
                ┌─────────▼─────────┐      ┌─────────▼─────────┐
                │  FecBulkAdapter   │      │   FecApiAdapter   │
                │ (cn/cm/ccl/itcont │      │ (api.open.fec.gov │
                │  pipe-delimited)  │      │  incremental)     │
                └─────────┬─────────┘      └─────────┬─────────┘
                          │   SourceAdapter interface │
                          └──────────┬────────────────┘
                          ┌──────────▼──────────┐
                          │   Staging tables    │  raw rows + provenance
                          └──────────┬──────────┘
                          ┌──────────▼──────────┐
                          │  Normalize + load   │  canonical schema (upserts)
                          └──────────┬──────────┘
                          ┌──────────▼──────────┐
                          │   Donor dedup job   │  donor + donor_link tables
                          └──────────┬──────────┘
                          ┌──────────▼──────────┐
                          │   Summary tables    │  rankings + breakdowns
                          └─────────────────────┘
```

**SourceAdapter** is the pluggability seam (PR-144 decision): each source implements `fetch() → Sequence<RawRecord>` into staging; everything downstream is source-agnostic. A Phase 7 state adapter is a new implementation, no schema change.

## 4. Data Model (canonical schema)

```sql
candidate    (id PK, fec_candidate_id UNIQUE, name, office, party, state, district)
committee    (id PK, fec_committee_id UNIQUE, name, type, designation)
candidate_committee (candidate_id FK, committee_id FK, linkage_type)  -- from ccl
donor        (id PK, canonical_name, employer, occupation, city, state, zip5)
donor_link   (donor_id FK, raw_contribution_id FK, match_rule)        -- dedup audit
contribution (id PK, source TEXT, source_record_id TEXT, amount NUMERIC,
              date, donor_id FK, committee_id FK,
              UNIQUE(source, source_record_id))                       -- idempotency
ingest_run   (id PK, source, started_at, finished_at, status, row_counts JSON)
```

- **Idempotency:** contributions upsert on `(source, source_record_id)` — FEC bulk `SUB_ID` / API `sub_id`. Re-running a load is a no-op on unchanged data.
- **Provenance:** every contribution carries `source` and `source_record_id`; `ingest_run` records each pipeline execution.
- **Attribution:** per PRD recommendation, rankings count **principal campaign committees only** (`ccl` linkage type P) in Phase 1.

## 5. Donor De-duplication

Conservative, deterministic, auditable — no probabilistic/ML matching in Phase 1. **Status: implemented (PR-156).**

### 5.1 Normalization (`DonorNormalizer`)

All normalization is a pure-function object with table-driven unit tests so rule changes are safe to make and re-test independently of the database.

- **Name:** FEC format is `"LAST, FIRST MIDDLE"`. Split on the first comma; take only the first token of the first-name part (discard middle name). Uppercase, collapse whitespace, replace punctuation (apostrophes, hyphens, etc.) with a space rather than removing it — conservative choice: `O'BRIEN` → `O BRIEN` and `OBRIEN` stay separate rather than risk false merges. Strip known suffixes (JR, SR, II, III, IV, ESQ, MD, PHD, DDS, RET) from the last-name tokens only. If the resulting last-name token list is empty (e.g., `"---, JOHN"`), the row is skipped.
- **Employer:** normalize to uppercase, collapse whitespace, then apply an alias map: `SELF`/`SELF-EMPLOYED`/`SELFEMPLOYED` → `SELF EMPLOYED`; `UNEMPLOYED`/`NOT-EMPLOYED` → `NOT EMPLOYED`; `N/A`/`NA`/`NONE`/`UNKNOWN`/`INFORMATION REQUESTED` → null (treated as blank). Plain names are stored as-is after uppercasing.
- **Zip:** extract the first 5 consecutive digits (handles 9-digit zip+4 and `NNNNN-NNNN` formats). Fewer than 5 digits → null (row skipped).

### 5.2 Match key and grouping

The match key is `(last_name, first_name, zip5, employer)` where blank employer is represented as empty string `""` rather than null. Blank employer contributions form their own group per `(name, zip5)`, **separate from any named-employer group**. This conservative "blank employer = own bucket" approach avoids the transitivity trap: if blank-employer rows were merged into either named employer, rows from `ACME` and `BIGCORP` could end up in the same donor through a chain of blank-employer intermediaries.

Match rules stored in `donor_link.match_rule`:
- `"name+zip5+employer"` — all four key components matched (employer was non-blank)
- `"name+zip5 (employer blank)"` — employer was blank; grouped on name+zip5 only

### 5.3 Job design (`DedupRunner`)

The job is **re-runnable** without re-ingesting: on every run it clears `donor_link`, nulls `contribution.donor_id`, and deletes all `donor` rows, then rebuilds from scratch. This means tightening or loosening a match rule is a one-command re-run (`./gradlew :pipeline:run --args="dedup"`).

Skipped rows (blank/un-normalizable name or zip) retain `contribution.donor_id = NULL` — they do not block the rest of the run, and their count is surfaced in `DedupSummary.skipped`.

### 5.4 Audit trail

Every `contribution` → `donor` mapping is recorded in `donor_link(donor_id, contribution_id, match_rule)`. The full set of raw contributions that collapsed into any single donor can always be recovered by joining `donor_link` → `contribution` → `staging_contribution`.

## 6. Ranking & Reconciliation

- **Summary tables:** `recipient_totals`, `donor_totals` — plain tables rebuilt by the pipeline at the end of each run (truncate-and-reload inside the run, swapped atomically via `RENAME TABLE`). Detail breakdowns are indexed queries on `contribution`.
- **Reconciliation report:** `reconcile --candidates=<ids>` fetches each candidate's published totals from the FEC API totals endpoint, compares against our aggregates, and emits a pass/fail table with deltas. Tolerance and known causes (unitemized money, filing lag) documented in the report output. Demo-gate sample: ≥5 candidates across president/senate/house.

## 7. Data Volumes & Performance

- Itemized individual contributions for a cycle: ~50–80M rows, ~10s of GB raw. Bulk load path uses MySQL `LOAD DATA LOCAL INFILE` into staging, then batched upserts; target full load < 2 hours on a dev machine.
- API adapter is for incremental top-ups only (rate limit: 1,000 calls/hour) — never for full loads. Always run `fec-bulk` first; `fec-api` watermarks from the latest successful run across both sources (`MAX(fec-api.finished_at, fec-bulk.finished_at)`), falling back to cycle start only when neither has ever run.
- FEC API quirks: requires `two_year_transaction_period` on every schedule_a request (omitting it returns 400); expects dates in `MM/dd/yyyy` format (not ISO `yyyy-MM-dd`). Register for an API key at https://api.data.gov/signup/.

## 8. Project Layout

```
CampaignFinances/
  settings.gradle.kts        # Gradle root — includes the pipeline subproject
  gradlew, gradle/           # Gradle wrapper (run all builds from this root)
  docker-compose.yml         # MySQL 8.4
  docs/
  pipeline/                  # this phase (Gradle subproject)
    src/main/kotlin/...      # adapters, normalize, dedup, reconcile
    src/main/resources/db/migration/       # Flyway (V1–V12)
    src/main/resources/logback.xml         # logging config (level via CF_LOG_LEVEL)
    src/test/kotlin/...
    src/test/resources/fixtures/fec-bulk/  # small real FEC file excerpts
```

## 9. Testing Strategy

- **Unit:** file parsing, normalization, dedup rules (table-driven), attribution logic.
- **Integration:** full pipeline run against fixture files into Testcontainers MySQL; assert rankings and idempotency (run twice, same state).
- **Reconciliation test:** fixture-based — known inputs produce known totals.

## 10. Risks

- **FEC bulk format quirks** (encoding, malformed rows): staging layer tolerates and logs bad rows rather than failing the run; bad-row count surfaces in `ingest_run`.
- **Dedup false merges:** mitigated by the conservative rule + audit trail; reconciliation of donor totals is best-effort (FEC publishes no canonical donor totals).
- **Load time on dev machines:** if >2h, fall back to a single-state slice for local dev, full data in CI/staging only.
- **Non-transactional DDL (MySQL):** a failed migration can leave the schema half-applied; mitigated by keeping Flyway migrations small/single-statement and rebuilding from empty in CI on every run.
