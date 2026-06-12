# Phase 1: Data Pipeline Proof — PRD

**Epic:** [PR-144](https://mgozer.atlassian.net/browse/PR-144)
**Parent docs:** [PRD.md](PRD.md), [PROJECT_PLAN.md](PROJECT_PLAN.md) (Phase 1)
**Status:** Draft
**Last updated:** 2026-06-11

## 1. Overview

Prove the foundation of the whole product: ingest real FEC data for the current 2025–2026 cycle into a database and produce correct ranked totals for recipients and donors. No UI, no API — correctness and reconciliation are the product of this phase.

## 2. Goals

- Ingest FEC candidate, committee, and itemized individual contribution data for the 2025–2026 cycle.
- Produce ranked totals: recipients by amount raised, donors by amount given.
- Establish trust: totals must reconcile against FEC.gov's published numbers.
- Design the ingestion layer so additional sources (state filings, Phase 7) plug in without schema changes.

### Non-goals (this phase)

- Any UI or HTTP API (Phases 2–3).
- Scheduled/automated daily refresh (Phase 4) — manual pipeline runs are fine here.
- State-level data (Phase 7).
- Historical cycles before 2025–2026.

## 3. Functional Requirements

### 3.1 Ingestion

- Pull from both FEC sources: `api.open.fec.gov` (API key, rate-limited) and FEC bulk data files (candidate master, committee master, itemized individual contributions).
- A pipeline run is repeatable: re-running on the same inputs produces the same database state (idempotent).
- Each record retains its source and the FEC filing/file it came from (provenance).

### 3.2 Data model

Entities, at minimum:

- **Candidate** — FEC candidate ID, name, office (president/senate/house), party, state/district.
- **Committee** — FEC committee ID, name, type, linked candidate(s).
- **Donor** — derived entity (see 3.3): canonical name, employer, occupation, location.
- **Contribution** — amount, date, donor reference, recipient committee reference, source provenance.

Schema must be source-agnostic: adding a state source later means a new ingestion adapter, not a schema migration (per the pluggable-ingestion decision on PR-144).

### 3.3 Donor de-duplication

- FEC itemized records identify donors only by name/employer/zip. Define and implement a **conservative** matching strategy: prefer leaving two records as separate donors over wrongly merging them.
- The strategy must be documented (rules, normalization, known limitations) and the merge decisions auditable (which raw records make up each canonical donor).

### 3.4 Ranking queries

- Top-N recipients by total raised in the cycle, highest to lowest.
- Top-N donors by total given in the cycle, highest to lowest.
- Per-recipient donor breakdown and per-donor recipient breakdown (the queries that will back the Phase 2 detail endpoints).

### 3.5 Reconciliation

- For a sample of candidates (at least 5, across offices), pipeline totals must match FEC.gov's published "total receipts"/itemized figures within a documented tolerance, with discrepancies explained (e.g., unitemized contributions, timing of filings).
- Reconciliation is a repeatable script/report, not a one-off manual check.

## 4. Demo Outcome (phase gate)

Live demo, per PROJECT_PLAN.md:

1. Run a query showing the **top 20 recipients** and **top 20 donors** ranked by amount.
2. Pick one candidate and reconcile their total against the number shown on FEC.gov, live.

## 5. Success Criteria

- Full ingestion runs end-to-end on real current-cycle data.
- Reconciliation passes for the sample set within documented tolerance.
- De-duplication strategy documented with audit trail.
- Automated tests: unit tests for parsing/normalization/dedup rules, integration test for the pipeline against a fixture dataset.
- Pipeline runbook documented (how to run, expected duration, data volumes).

## 6. Open Questions

- **Itemized-only ranking:** itemized data covers donations over $200. Do donor rankings note this caveat now, and how are unitemized totals represented for recipients? (Carryover from main PRD.)
- **Committee → candidate attribution:** money to joint fundraising committees and PACs — counted toward candidates in Phase 1, or candidate-committee money only? Recommendation: principal campaign committees only in Phase 1; broader attribution as a later enhancement.
- **Database choice:** TDS decision (volume: itemized contributions are tens of millions of rows per cycle).
