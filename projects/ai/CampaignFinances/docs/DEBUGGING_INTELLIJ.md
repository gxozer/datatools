# Debugging the Pipeline in IntelliJ

**Prerequisite:** open the project at `CampaignFinances/` (the Gradle root) and let Gradle sync. The `pipeline` module appears as `campaign-finances.pipeline`.

## 1. Debugging the CLI

### Option A — IntelliJ Application run configuration (recommended)

1. Run → Edit Configurations → **+** → **Kotlin**
2. Main class: `com.campaignfinances.pipeline.MainKt`
3. Use classpath of module: `campaign-finances.pipeline.main`
4. Program arguments: e.g. `ingest --source=fec-bulk --cycle=2026` (or `migrate`, `dedup`, `reconcile ...`)
5. Environment variables (only if overriding defaults): `CF_DB_URL`, `CF_DB_USER`, `CF_DB_PASSWORD`, later `FEC_API_KEY`
6. Set breakpoints, hit **Debug**

**Which database does this connect to?** The Docker Compose MySQL. With no env vars set, `DbConfig.fromEnv()` defaults to `jdbc:mysql://localhost:3307/campaign_finances` (`cf`/`cf`), and host port 3307 is the compose container's published port (`127.0.0.1:3307:3306` in `docker-compose.yml`). Start it first with `docker compose up -d --wait`, or you'll get `Communications link failure`.

Three things to be clear about:

- It is the **same long-lived dev database** the CLI runs use — including the full ~25M-row staging dataset. Debugging an `ingest` (which truncates staging tables) modifies that real data.
- It is **not** your local mysqld on 3306 — that server is untouched; the project uses 3307 specifically to avoid it.
- It is **not** Testcontainers — throwaway containers exist only for test runs (§2). Application debug sessions always hit the compose DB.

To debug against a different/scratch database instead (e.g., to avoid disturbing loaded data), set `CF_DB_URL`/`CF_DB_USER`/`CF_DB_PASSWORD` in the run configuration's environment variables.

### Option B — attach to a Gradle-launched run

When you need the exact Gradle execution environment:

```bash
./gradlew :pipeline:run --args="ingest --source=fec-bulk --cycle=2026" --debug-jvm
```

The JVM suspends on startup waiting for a debugger. In IntelliJ: Run → Edit Configurations → **+** → **Remote JVM Debug** (defaults: localhost:5005) → Debug.

## 2. Debugging tests

Click the gutter ▶ next to any test class/method and choose **Debug**. Two things to know:

- **Keep the default test runner (Gradle).** Settings → Build Tools → Gradle → "Run tests using: Gradle (Default)". Our `build.gradle.kts` test task pins `DOCKER_API_VERSION=1.44` / `api.version` — required because Docker Engine 29 rejects docker-java's default. Switching the runner to "IntelliJ IDEA" skips that configuration and Testcontainers tests fail with *"Could not find a valid Docker environment"*. If you must use the IntelliJ runner, add `DOCKER_API_VERSION=1.44` to the run configuration's environment manually.
- **Testcontainers + breakpoints:** while you're paused at a breakpoint the throwaway MySQL container stays up. To query it mid-debug, evaluate `mysql.jdbcUrl`, `mysql.username`, `mysql.password` in the debugger (Alt/Opt+F8) and connect with any client. The container dies when the test JVM exits.

## 3. Inspecting the database

### Quickest: mysql prompt through the container (no client install)

```bash
docker exec -it campaignfinances-mysql-1 mysql -ucf -pcf campaign_finances
```

```sql
SELECT COUNT(*) FROM staging_contribution;          -- ~25.3M after a full load
SELECT * FROM ingest_run ORDER BY id DESC LIMIT 5;
```

### IntelliJ Database tool window (best for browsing)

Database tool window → **+** → Data Source → MySQL:

- Host `localhost`, port `3307`, database `campaign_finances`, user/password `cf`/`cf`

If using your Mac's own `mysql` client instead: `mysql -h127.0.0.1 -P3307 -ucf -pcf campaign_finances` — the `-P3307` is mandatory (default 3306 is the unrelated local mysqld), and use `-h127.0.0.1` rather than `localhost` (which routes to the local mysqld's unix socket).

Heads-up: `staging_contribution` holds ~25M rows after a full cycle load — always use `LIMIT`, and prefer the indexed columns (`sub_id`) in ad-hoc WHERE clauses.

## 4. Tips for high-volume code paths

The parse/load loop touches 25M+ lines; a plain breakpoint inside `FecBulkParser.parse` or the `loadFile` loop will effectively hang the run. Instead:

- **Conditional breakpoints:** right-click the breakpoint → condition, e.g. `line.contains("SMITH")` or `bad > 0` (break only on the first malformed row)
- **Non-suspending logging breakpoints:** right-click → uncheck "Suspend", set "Evaluate and log: `line`" — printf debugging without editing code
- **Inspect the LOAD DATA temp file:** breakpoint in `StagingLoader.load` on the `executeUpdate` line — the temp TSV path is in `tsv`; the file is deleted in the `finally`, so look while paused
- **Use fixture runs, not full loads:** `ingest --source=fec-bulk --cycle=2026 --dir=pipeline/src/test/resources/fixtures/fec-bulk` runs the whole pipeline against 4 tiny files — debug there first, full data only when the question is about scale

## 5. Common issues

| Symptom | Cause / fix |
|---|---|
| `Migrator` or other classes red in editor | Gradle project not synced — Gradle tool window → Reload All Gradle Projects |
| "Invalid Gradle JDK" banner | Settings → Build Tools → Gradle → Gradle JVM → Project SDK |
| Testcontainers "Could not find a valid Docker environment" | Docker Desktop not running, or IntelliJ test runner bypassing the `DOCKER_API_VERSION` pin (see §2) |
| `Communications link failure` at `localhost:3307` | Compose MySQL not up: `docker compose up -d --wait` |
| LOAD DATA fails with "Loading local data is disabled" | Connection missing `allowLoadLocalInfile=true` — bulk loads must connect via `DbConfig.urlWithLocalInfile`, not the plain URL |
