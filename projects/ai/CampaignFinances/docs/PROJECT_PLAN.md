# Campaign Finances Website — Project Plan

**Epic:** [PR-144](https://mgozer.atlassian.net/browse/PR-144)
**Status:** Draft
**Last updated:** 2026-06-11

Each phase ends with a working demo. No phase is "done" until its demo can be shown live.

## Phase 1 — Data pipeline proof

**Scope:** Ingest FEC data (API + bulk downloads) for the current 2025–2026 cycle into a database. Define the schema for candidates, committees, donors, and contributions. First pass at donor de-duplication (name/employer/zip matching, conservative).

**Demo:** Run a query (CLI or SQL) live that shows the top 20 recipients and top 20 donors ranked by amount — and reconcile one candidate's total against the number shown on FEC.gov.

**Exit criteria:** ingestion runs end-to-end on real data; totals reconcile with FEC within a documented tolerance; de-duplication strategy documented.

## Phase 2 — Backend API

**Scope:** Kotlin REST API on top of the Phase 1 database: ranked recipients endpoint, ranked donors endpoint, recipient detail (its donors ranked), donor detail (its recipients ranked), pagination, last-refresh timestamp endpoint.

**Demo:** Live API calls (curl/Postman) returning ranked JSON for all four views, with pagination working.

**Exit criteria:** all endpoints covered by unit + integration tests; API contract documented (OpenAPI).

## Phase 3 — Web MVP

**Scope:** React + TypeScript web app: two tabs (Recipients, Donors), both detail pages, data-freshness indicator. Deployed to a staging URL.

**Demo:** Browse the staging site live — switch tabs, click into a recipient and a donor, see real current-cycle data.

**Exit criteria:** component + end-to-end tests passing; deployed and reachable on staging.

## Phase 4 — Daily refresh automation

**Scope:** Scheduled daily ingestion job, idempotent re-runs, stale-data detection, failure alerting, freshness timestamp wired through API to UI.

**Demo:** Show yesterday's and today's data side by side after an automatic (not manually triggered) refresh; kill a refresh and show the failure alert + stale indicator.

**Exit criteria:** refresh has run unattended for 7 consecutive days; failure path tested.

## Phase 5 — Mobile apps

**Scope:** iOS and Android apps with the same four views, per the cross-platform strategy chosen in the TDS (React Native vs. Kotlin Multiplatform vs. native — TDS decision).

**Demo:** Same flows as the Phase 3 demo, on a physical iPhone and Android device, against live data.

**Exit criteria:** feature parity with web; mobile test suites passing; internal test-track builds distributed (TestFlight / Play internal testing).

## Phase 6 — Hardening & public launch

**Scope:** Performance (list endpoints under load), accessibility pass, terms of service (incl. FEC no-solicitation restriction), production deployment, app store submissions, public documentation.

**Demo:** Production URL live to the public; apps approved or in review in both stores.

**Exit criteria:** load test meets targets; ToS published; monitoring/alerting in place.

## Phase 7 — State-level data (Phase 2 of data strategy)

**Scope:** First state filing source plugged into the ingestion layer, proving the multi-source design. Judges/state candidates appear in rankings with a source label.

**Demo:** A state candidate (e.g., a judge) appearing in the recipients tab alongside federal candidates, with its data traced to the state source.

**Exit criteria:** second source runs in the same daily pipeline; adding a third source requires no schema change.

## Dependencies & sequencing

- Phases 1 → 2 → 3 are strictly sequential.
- Phase 4 can start in parallel with Phase 3 (both depend on Phase 2).
- Phase 5 depends on the TDS cross-platform decision and the Phase 2 API; UI work can begin once the API contract is frozen.
- Phase 7 is post-launch and independently schedulable.

## Cross-cutting (every phase, per PR-134)

- Jira child tickets created per phase before work starts; kept up to date.
- Automated tests written within the phase, not after.
- Docs updated as part of each phase's exit criteria.
- Agents: test-runner on code changes, docs-updater, and code-review agent active from Phase 1 onward.
