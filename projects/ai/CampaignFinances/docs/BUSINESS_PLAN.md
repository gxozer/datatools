# Campaign Finances Website — Business Plan

**Epic:** [PR-144](https://mgozer.atlassian.net/browse/PR-144)
**Status:** Draft
**Last updated:** 2026-06-11

## 1. Executive Summary

A consumer-friendly web and mobile application that shows who raises and who donates campaign money in US elections, ranked and refreshed daily from official FEC data. Existing tools (FEC.gov, OpenSecrets) are built for researchers; this product targets everyday voters and journalists who want the answer in two taps.

## 2. Problem

- Campaign finance data is public but hard to consume: raw FEC tooling is built for compliance and research, not browsing.
- During election cycles, interest spikes (debates, news stories), but no mainstream product answers "who's funding whom" simply, on mobile, with fresh data.

## 3. Solution

- Two ranked views — top money recipients (candidates) and top donors — for the current election cycle.
- Drill-down detail pages connecting donors and recipients in both directions.
- Daily automated refresh; freshness shown in the UI.
- Web, iOS, and Android from launch.

## 4. Market

- **Primary:** US voters interested in elections (tens of millions during a cycle; traffic is highly seasonal, peaking around primaries and the general election).
- **Secondary:** journalists, researchers, civic-tech organizations, campaign staff.
- **Phase 2 expansion:** state-level races (judges, governors, legislatures) — a long tail underserved by national tools.

## 5. Competition

| Competitor | Strength | Gap we exploit |
|---|---|---|
| FEC.gov | Authoritative, complete | Built for compliance/research; poor consumer UX, no mobile apps |
| OpenSecrets | Rich analysis, donor rollups | Editorial/research focus; restricted API; not a simple ranked browser |
| Ballotpedia | Broad election coverage | Finance data is secondary, not ranked or daily |

Differentiator: simplicity (two ranked tabs), freshness (daily), and mobile-first reach.

## 6. Revenue Model (candidates, to validate)

1. **Free + ads** — civic content draws seasonal spikes; ads monetize traffic without paywalling public data.
2. **Pro tier** — alerts, CSV/API export, historical cycles, saved searches for journalists and researchers.
3. **API licensing** — cleaned, de-duplicated, daily-refreshed dataset for newsrooms and civic-tech apps.
4. **Sponsorship/grants** — civic transparency foundations (Knight Foundation, Democracy Fund) fund this category.

Recommended start: free product to build audience; validate Pro tier with journalists in the first cycle.

## 7. Cost Structure

- **Build:** one small team (per PR-140 stack: Kotlin backend, React/TS web, iOS/Android apps).
- **Run:** data pipeline + storage (FEC bulk data is tens of GB per cycle), API hosting, mobile store fees. FEC data itself is free.
- **Ongoing:** Phase 2 state-data ingestion is the main cost driver (50 different formats).

## 8. Risks

- **Seasonality:** traffic collapses between cycles → mitigate with Pro/API revenue and state races (Phase 2) which run year-round.
- **Data correctness/reputation:** mis-attributed donations are reputationally damaging → reconcile against FEC totals, show sources, publish methodology.
- **Donor de-duplication accuracy:** FEC identifies donors by name/employer/zip; bad matching undermines the donors tab → conservative matching, documented in TDS.
- **Legal/compliance:** FEC data use is permitted, but contributor data may not be used for solicitation — terms of service must reflect this.

## 9. Milestones

1. PRD + TDS approved (PR-147, TDS ticket to follow)
2. MVP: web app, both tabs + detail pages, daily FEC refresh
3. iOS + Android launch
4. Pro-tier validation with journalist users
5. Phase 2: first state-level data source
