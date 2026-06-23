# Campaign Finances

A website (web, iOS, Android) showing who raises and who donates campaign money in US elections — ranked highest to lowest, refreshed daily from official FEC data.

**Jira epic:** [PR-144](https://mgozer.atlassian.net/browse/PR-144) · **Status:** Phase 1 in progress (data pipeline)

## What it does

- **Recipients tab** — candidates (president, senate, house) ranked by total raised in the current election cycle
- **Donors tab** — donors ranked by total given
- Each entry links to a detail page: a recipient's donors, or a donor's recipients, ranked with totals
- Data refreshes daily; the UI shows last-refresh time

## Documentation

| Document | Purpose |
|---|---|
| [docs/PRD.md](docs/PRD.md) | Product requirements for the whole epic |
| [docs/BUSINESS_PLAN.md](docs/BUSINESS_PLAN.md) | Market, competition, revenue model, risks |
| [docs/PROJECT_PLAN.md](docs/PROJECT_PLAN.md) | 7 phases, each gated on a live demo |
| [docs/PRD_PHASE1.md](docs/PRD_PHASE1.md) | Phase 1 requirements: FEC data pipeline proof |
| [docs/TDS_PHASE1.md](docs/TDS_PHASE1.md) | Phase 1 technical design (Kotlin, MySQL 8.4, adapters) |
| [docs/DEBUGGING_INTELLIJ.md](docs/DEBUGGING_INTELLIJ.md) | Debugging the pipeline in IntelliJ (CLI, tests, Testcontainers, DB) |

## Tech stack

- **Pipeline / backend:** Kotlin (JVM 21), Gradle, MySQL 8.4, Flyway, jOOQ
- **Frontend (Phase 3+):** React + TypeScript; iOS and Android per the TDS cross-platform decision
- **Data:** FEC bulk files + `api.open.fec.gov` (federal, Phase 1); state filings later (Phase 7)

## Quick start (Phase 1 pipeline)

Prerequisites: JDK 21+, Docker Desktop running, host port 3307 free.

```bash
# start MySQL 8.4
docker compose up -d --wait

# apply schema migrations
./gradlew :pipeline:flywayMigrate   # expect: "Applied 12 migration(s)" on first run

# run the test suite (uses its own throwaway Testcontainers MySQL)
./gradlew test

# see available pipeline commands
./gradlew :pipeline:run -q
```

Open the project in IntelliJ by opening this root folder — the Gradle project is auto-detected (`settings.gradle.kts` lives here; `pipeline` is a subproject).

Database connection (override with `CF_DB_URL`, `CF_DB_USER`, `CF_DB_PASSWORD`):
`jdbc:mysql://localhost:3307/campaign_finances`, user/password `cf`/`cf`.

## Project layout

```
CampaignFinances/
├── README.md
├── settings.gradle.kts         # Gradle root — includes the pipeline module
├── gradlew, gradle/            # Gradle wrapper
├── docker-compose.yml          # MySQL 8.4 (host port 3307; --local-infile=1)
├── docs/                       # PRD, TDS, business plan, project plan
└── pipeline/                   # Phase 1: FEC data pipeline (Gradle Kotlin module)
    ├── src/main/kotlin/com/campaignfinances/pipeline/
    │   ├── Main.kt             # CLI entry point
    │   ├── cli/                # Command interface + one class per command
    │   └── db/                 # DbConfig, Migrator
    ├── src/main/resources/db/migration/   # Flyway SQL migrations (V1–V12)
    └── src/test/kotlin/        # unit + Testcontainers integration tests
```

## Development workflow

Per [PR-134](https://mgozer.atlassian.net/browse/PR-134): every piece of work has a Jira ticket under the epic; PRD/TDS precede implementation; every ticket ships with its tests; each phase ends with a live demo (see PROJECT_PLAN.md). Phase 1 implementation tickets: PR-152, PR-153, PR-154 (done) → PR-156–PR-160.

### Conventions

- Classes/interfaces at architectural seams (`Command`, `SourceAdapter`, pipeline stages); pure functions for rule logic (normalization, dedup)
- Flyway migrations are small and single-statement (MySQL DDL commits implicitly)
- Contributions are idempotent on `UNIQUE(source, source_record_id)` — re-running any ingest is safe

### Known environment quirks

- **Port 3307, not 3306** — dev machines often run a local mysqld on 3306
- **Docker Engine 29+**: the test task pins Docker API version 1.44 (older docker-java default is rejected)
- **Flyway Gradle plugin is not used** (incompatible with Gradle 9); `./gradlew :pipeline:flywayMigrate` delegates to the pipeline CLI's `migrate` command
