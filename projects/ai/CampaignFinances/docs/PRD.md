# Campaign Finances Website — Product Requirements Document (PRD)

**Epic:** [PR-144](https://mgozer.atlassian.net/browse/PR-144)
**Status:** Draft
**Last updated:** 2026-06-11

## 1. Overview

A cross-platform application (web, iOS, Android) that shows who raises and who donates campaign money in US elections, ranked by amount, refreshed daily from official sources.

## 2. Goals

- Make campaign finance data easy to browse for the general public.
- Always answer two questions at a glance: *who has collected the most money* and *who has given the most money* in the current election cycle.
- Keep data fresh automatically (daily updates, no manual steps).

### Non-goals (this epic)

- State-level filings (judges, state races) — deferred to Phase 2.
- Historical cycle browsing / cycle selector — current cycle only for now.
- User accounts, alerts, or personalization.

## 3. Users

General-public users (voters, journalists, researchers) who want a simple ranked view of campaign money without navigating raw FEC tooling.

## 4. Functional Requirements

### 4.1 Recipients tab (default)

- Lists individuals collecting campaign donations: candidates for president, senate, and house (federal only in Phase 1).
- Sorted by total amount raised in the current election cycle, highest to lowest.
- Each row shows at minimum: name, office sought, party, state (where applicable), total raised.
- Each row links to a **recipient detail page**.

### 4.2 Donors tab

- Lists donors sorted by total amount given in the current election cycle, highest to lowest.
- Each row shows at minimum: donor name, total given.
- Each row links to a **donor detail page**.

### 4.3 Recipient detail page

- Shows the detailed list of donors to that recipient, sorted highest to lowest, with the recipient's grand total at the top and contribution dates.

### 4.4 Donor detail page

- Mirror of the recipient detail page: candidates/committees this donor gave to, sorted highest to lowest, with the donor's grand total at the top and contribution dates.

### 4.5 Data freshness

- Data updates daily via an automated pipeline.
- The UI displays the timestamp of the last successful data refresh.

## 5. Data Source

- **Phase 1 (this epic):** FEC — `api.open.fec.gov` API plus FEC bulk data downloads. Covers all federal candidates and itemized individual contributions.
- **Phase 2 (future):** state-level filing portals (judges, state races). The ingestion layer must be designed so new sources can be plugged in (see TDS).
- **Timeframe:** current federal election cycle (2025–2026).

## 6. Platforms & Tech Stack

Per [PR-140](https://mgozer.atlassian.net/browse/PR-140):

- Backend: Kotlin
- Frontend: React + TypeScript (web)
- Mobile: iOS and Android are in scope for this epic; the cross-platform strategy (React Native vs. Kotlin Multiplatform vs. native) is a TDS decision.

## 7. Success Criteria

- Both tabs render ranked lists from real FEC data for the current cycle.
- Detail pages show correct, sorted donor/recipient breakdowns that reconcile with FEC totals.
- Daily refresh runs unattended; a failed refresh is visible (stale-data indicator and/or alert).
- Feature parity across web, iOS, and Android at launch.

## 8. Open Items

- Donor identity de-duplication: FEC itemized data identifies donors by name/employer/zip — matching the "same" donor across contributions needs a defined strategy (TDS).
- Itemized contributions only cover donations over $200; decide how to present unitemized totals.
- Committee vs. candidate attribution: define how money to PACs/joint fundraising committees maps to candidates.
