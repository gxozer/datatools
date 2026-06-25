# Campaign Finances — Claude Code Instructions

**Jira epic:** [PR-144](https://mgozer.atlassian.net/browse/PR-144)  
**Workflow standard:** [PR-134](https://mgozer.atlassian.net/browse/PR-134) — every piece of work gets a Jira ticket; PRD/TDS precede implementation; every ticket ships with tests.

## Build and test

Prerequisites: JDK 21+, Docker Desktop running, host port 3307 free.

```bash
# start MySQL (required for integration tests)
docker compose up -d --wait

# run the full test suite
./gradlew test

# apply schema migrations to the dev DB
./gradlew :pipeline:flywayMigrate

# run the pipeline CLI
./gradlew :pipeline:run -q --args="ingest --source=fec-bulk --cycle=2026"
```

Tests are self-contained — they spin up their own throwaway Testcontainers MySQL. You do not need `docker compose` running to run tests, but Docker Engine must be available.

## Tech stack

- **Language:** Kotlin (JVM 21), Gradle Kotlin DSL
- **Database:** MySQL 8.4 (host port 3307 in dev; `cf`/`cf` credentials)
- **Migrations:** Flyway (SQL only, V1–V12+; one statement per file)
- **HTTP client:** Ktor CIO with kotlinx-serialization JSON
- **Tests:** JUnit 5 + Testcontainers (MySQL) + Ktor `MockEngine`
- **Frontend (Phase 3+):** React + TypeScript, iOS, Android — not yet in this repo

## Architecture — pipeline module

```
pipeline/src/main/kotlin/com/campaignfinances/pipeline/
├── Main.kt                  # CLI entry point — calls Cli, then exitProcess()
├── cli/
│   ├── Command.kt           # interface: run(args): Int
│   ├── Cli.kt               # dispatch: maps first arg to a Command
│   ├── IngestCommand.kt     # --source / --cycle / --dir options; dispatches to BulkIngestRunner
│   └── ...                  # MigrateCommand, DedupCommand, ReconcileCommand
├── db/
│   ├── DbConfig.kt          # fromEnv() reads CF_DB_URL / CF_DB_USER / CF_DB_PASSWORD
│   └── Migrator.kt
└── ingestion/
    ├── BulkIngestRunner.kt  # interface: ingest(cycle, localDir?): IngestSummary
    ├── IngestRunRepository.kt   # all ingest_run table reads/writes go here
    ├── CanonicalLoader.kt       # staging → canonical load, reused by both adapters
    ├── FecBulkAdapter.kt        # bulk file download + staging load
    ├── FecApiAdapter.kt         # incremental API top-ups
    ├── FecApiClient.kt          # Ktor wrapper: throttle + 429 backoff
    └── StagingLoader.kt
```

**Key invariants:**
- `UNIQUE(source, source_record_id)` dedupes canonical `contribution` within one source. Cross-source dedup is a known gap (PR-155 scoping decision; see `docs/TEST_PLAN_PHASE1.md §4`).
- All `ingest_run` table access goes through `IngestRunRepository` — do not query that table from adapters directly.
- Flyway migrations are single-statement; MySQL DDL auto-commits so multi-statement files would silently leave partial state.
- `CanonicalLoader.loadContributions` is shared by both adapters unchanged — the bulk staging format (`MMddyyyy` dates, pipe-delimited) is the source of truth; the API adapter converts to match it.

## Code style

These rules are strictly enforced here — flag violations in code review.

- **KDoc every class and function**: contract, `@param`/`@return`, and rationale with ticket/TDS cross-references where non-obvious.
- **Flatten Kotlin**: prefer plain `if`/`else` with early returns over stacked `?:` chains, `let`/`run`/`also` scope functions, and nested lambda blocks. A flat version that a junior can read is always preferred over a clever one-liner.
- **Named helpers over nesting**: extract a named private function rather than nesting a lambda inside a `.use {}` inside a `when`.
- **Inline comments on non-obvious mechanics** — encoding choices, coroutine bridging (`runBlocking`), why a guard exists — not on what the code obviously does.
- **No clever abbreviations**: `connection`, not `conn`; `statement`, not `stmt`.

## Jira workflow

- Use `mcp__jira__*` tools — never `bd` (beads) commands.
- **Never transition a ticket** (In Progress / Done) unless the user explicitly asks.
- **Never commit or push** without explicit instruction from the user.
- After finishing work on a ticket, post a comment summarising what was done.
- File a new Jira ticket for every independent task — refactors, doc passes, and follow-up gaps all count.

## Environment variables

| Variable | Purpose | Required for |
|---|---|---|
| `CF_DB_URL` | JDBC URL (default: `jdbc:mysql://localhost:3307/campaign_finances`) | Non-default DB |
| `CF_DB_USER` | DB user (default: `cf`) | Non-default DB |
| `CF_DB_PASSWORD` | DB password (default: `cf`) | Non-default DB |
| `FEC_API_KEY` | api.open.fec.gov API key | `ingest --source=fec-api` only |

## Known environment quirks

- **Port 3307, not 3306** — dev machines often have a local mysqld on 3306.
- **Docker Engine 29+** — the test task pins Docker API version 1.44; older versions are rejected.
- **Flyway Gradle plugin not used** — incompatible with Gradle 9; `flywayMigrate` delegates to the pipeline CLI's `migrate` command.
- Shell `cp`/`mv`/`rm` may be aliased to `-i` (interactive); always pass `-f` / `-rf` to avoid hanging.
