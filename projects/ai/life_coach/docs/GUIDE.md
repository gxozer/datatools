# Developer Context Guide — Life Coach App

**Epic:** [PR-133](https://mgozer.atlassian.net/browse/PR-133)
**Date:** 2026-06-05

This guide is for anyone — engineer, designer, product manager, or artificial intelligence (AI) agent — who wants to build a complete understanding of this project before contributing. Read the documents in the order listed. Each section explains what the document contains, what decisions it captures, and what questions it leaves open.

---

## What This Project Is

The Life Coach App is an AI-powered personal development platform that:
- Connects to a user's calendar and detects free time
- Proactively surfaces nearby activities matched to the user's personal goals — without the user having to search or configure anything
- Writes accepted recommendations directly into the user's calendar
- Sends a post-visit email to collect feedback
- Builds a content database using AI generation, community confirmation, and user-created content
- Rewards engaged users with reputation and creator benefits

The app serves **anyone with a goal** — from a 22-year-old trying to make friends, to a 78-year-old writing her memoirs, to someone who wants to visit every pub in Dublin.

---

## Document Map

```
docs/
├── GUIDE.md                     ← You are here
├── PR-133-review.md             ← Where this project started (epic review)
├── business-plan.md             ← The full product and business strategy
├── profile-and-goal-examples.md ← User personas and detailed scenarios
├── content-pipeline.md          ← AI content generation and lifecycle system
└── dating-matching.md           ← Dating & activity matching feature spec
```

---

## Reading Order

### 1. `PR-133-review.md` — Start Here
**What it is:** A review of the original Jira epic (PR-133) that defines this project.

**Read it to understand:**
- What the product owner originally described
- What was missing from the initial brief (no Minimum Viable Product (MVP) definition, no acceptance criteria, no Request for Comments (RFC) tickets)
- The raw feature list as first articulated: calendar integration, AI recommendations, social features, voice commands, scoring/levelling

**Key gaps flagged:**
- No acceptance criteria on the epic
- RFC tickets for calendar integration and scoring/levelling never created
- No feature prioritisation or MVP boundary

**After reading this:** You know what the original idea was and why it needed elaboration before development could start.

---

### 2. `business-plan.md` — The Strategic Foundation
**What it is:** A 14-section business plan (v1.4) covering the full product strategy, market opportunity, feature roadmap, business model, and technical direction.

**Read it to understand:**
- The product vision and the 6 core interaction mechanics
- Who the users are — all age groups, all goal types, no demographic restriction
- What gets built in MVP vs Phase 2 vs Phase 3
- How the app makes money
- What the competitive landscape looks like and what our moat is
- High-level technical architecture
- Privacy and compliance requirements
- Risks and open questions

**Critical sections for engineers:**

| Section | Why it matters |
|---|---|
| **§3 Product Vision** | Defines the 6 core mechanics — every feature flows from these |
| **§5 Core Features** | MVP scope — build only this first |
| **§5a AI Content Pipeline** | Summary of the content lifecycle system |
| **§6 Goal Profiles** | The full taxonomy of user profiles including older adult profiles |
| **§10 Technical Architecture** | Stack decisions and pointers to PR-140 |
| **§12 Risks** | 12 risks with mitigations — read before starting any new feature |
| **§14 Open Questions** | 23 unresolved product and technical questions — check here before making assumptions |

**Key decisions made:**
- Freemium Software as a Service (SaaS): Free / Pro ($9.99) / Elite ($24.99)
- Calendar write-back is in MVP (not just read)
- Post-visit email feedback loop is in MVP
- Reputation system and User-Generated Content (UGC) are Phase 2
- Creator revenue share is Phase 3
- React Native frontend, Python or Node backend (decided in PR-140)
- Claude Application Programming Interface (API) (Anthropic) for AI recommendations and content generation

**After reading this:** You understand the full product strategy, what to build first, and how it makes money.

---

### 3. `profile-and-goal-examples.md` — The Users
**What it is:** 27 detailed user personas (v3.0) with full scenarios, covering every major user segment the app must serve.

**Read it to understand:**
- What real users look like across age, goal type, personality, and life stage
- How the app's core mechanics play out in practice — zero-setup, proactive discovery, one-tap calendar, post-visit email, reputation, UGC
- What tone the AI must use for each segment (blunt for the burnt-out lawyer, warm for the widower, dry for the novelist, clinical for the sprinter)
- What "good" looks like for each user — not metrics, but human moments
- Edge cases: grief, illness, divorce, bereavement, chronic conditions, cognitive decline

**Key product principles illustrated across the profiles:**

| Principle | Where to see it |
|---|---|
| Zero-setup discovery | Maria (no list import), Tom (work trip surfaces stadium), Patrick (discovery mode) |
| One tap → calendar | Every accepted recommendation in every profile |
| Post-visit email loop | Maria (pub visit email), Derek (park review), Karen (walk feedback shaping future suggestions) |
| Reputation building | Bill (unexpected reviewer), Dorothy (highest-rated in Bath without knowing) |
| UGC creator journey | Maria (pub trails, viral Sunday list), Priya (street art map), Carlos (pintxos authority) |
| Older adult handling | Profiles 21–27: Eileen, Arthur, Margaret, Dorothy, Frank & Sheila, Desmond, Joe |
| Tone calibration | Laura (blunt), Michael (grief-aware, never pushy), Sam (introvert-safe), Aisha (clinical) |
| Setback handling | Every profile — the app never shames, never nags, always picks up gently |

**Dating scenario profiles (28–30):** Maria meets Ciarán through 14 shared pub visits; Sam matches Laila from the same book club; Michael the widower enables dating 11 months in, on his own terms.

**After reading this:** You understand who you are building for, how they interact with the product, what the AI's voice must feel like, and how the dating feature plays out across very different life situations.

---

### 4. `dating-matching.md` — The Dating Feature
**What it is:** Full product spec (v1.0) for the opt-in activity-based dating and matching system.

**Read it to understand:**
- Why this is fundamentally different from existing dating apps (behavioural matching vs. self-reported)
- The exact 7-step matching flow: opt-in → match detected (no identity revealed) → questions → mutual Q&A → reveal → messaging → activity suggestions
- How the matching algorithm works and what signals it uses
- What filters users set and how bilateral filtering works
- What is and isn't visible before mutual Q&A (nothing identifying before reveal)
- Safety model: block/report at every stage, contact sharing requires explicit dual consent
- Monetisation: included in Pro; Elite unlocks power features; Boost as one-time purchase
- Three worked examples: Maria meets Ciarán via shared pub visits; Sam matches with Hana from the book club; Michael the widower engaging on his own timeline

**Critical design constraints:**
- Photos and identity revealed **only** after both sides answer each other's questions
- Under-18s ineligible regardless of filter settings
- Filters are bilateral — both users' constraints must pass
- Match expires silently in 7 days if not mutually engaged — no notification of expiry
- Feature is off by default and ships in Phase 2, after the core coaching product is established

**After reading this:** You can design the matching engine, the Q&A flow, the reveal mechanism, and the safety controls.

---

### 5. `content-pipeline.md` — The Content Engine
**What it is:** A product and technical specification (v1.0) for the AI content generation, maintenance, and retirement system.

**Read it to understand:**
- The 7 content states and how items move between them
- The 6 triggers that cause content to be generated
- The 5-step generation process: source aggregation → validation → AI generation → confidence scoring → publication
- How freshness is monitored and acted on continuously
- What causes content to be deprecated and archived
- How user-generated content participates in the same lifecycle
- The 5-level content quality ladder (AI Draft → Expert Verified)
- Cost management guardrails

**Critical technical details:**

| Detail | Section |
|---|---|
| Content states and transitions | §3 |
| Generation triggers | §4 |
| Third-party data sources (Google Places, Foursquare, OpenStreetMap (OSM), Eventbrite, Wikipedia) | §5, Step 1 |
| Confidence scoring thresholds | §5, Step 4 |
| Scheduled background jobs and frequencies | §6 |
| Deprecation triggers and process | §7 |
| UGC lifecycle | §8 |
| Generation cost controls | §10 |
| Operational ownership | §11 |

**After reading this:** You can design the content database schema, the background job system, and the AI generation prompts.

---

## Key Decisions Not Yet Made

These are open questions that must be resolved before or during development. The full list is in `business-plan.md §14`, but the most important for engineers starting now:

| # | Question | Blocks |
|---|---|---|
| 1 | iOS (Apple's mobile operating system) / Android / Web — what is the MVP platform? | All frontend work |
| 2 | Which AI provider? (Claude assumed, RFC pending) | Content generation, recommendation engine |
| 16 | Content database at launch — internal curation vs. third-party APIs vs. combination? | Content pipeline kickoff |
| 17 | Email feedback loop design — timing, frequency, opt-out | Post-visit email feature |
| 18 | Reputation scoring model — weights, helpfulness votes, gaming prevention | Reputation system (Phase 2) |
| 22 | Who owns content pipeline operations? | Team planning |
| dating-1 | Should both users see the match simultaneously, or one first? | Dating matching engine |
| dating-2 | Minimum reputation/activity level to use dating feature? | Trust and safety |
| dating-3 | How do we moderate questions to prevent identifying information? | Content moderation |

---

## Jira Tickets

| Ticket | Title | Status |
|---|---|---|
| [PR-133](https://mgozer.atlassian.net/browse/PR-133) | Life Coach App (Epic) | To Do |
| [PR-134](https://mgozer.atlassian.net/browse/PR-134) | Development Practices | To Do |
| [PR-140](https://mgozer.atlassian.net/browse/PR-140) | Tech Stack | To Do |
| [PR-141](https://mgozer.atlassian.net/browse/PR-141) | Business Plan | To Do |

**Start with PR-134 and PR-140** — development practices and tech stack must be resolved before implementation tickets can be written. Once those are done, the next logical tickets are:
- RFC: Calendar integration
- RFC: Scoring and levelling system
- RFC: AI provider selection
- MVP: Authentication
- MVP: Calendar read (read-only); invite delivery via .ics email — no calendar write permission needed
- MVP: Content database seed and generation pipeline
- MVP: Goal profiles and onboarding
- MVP: Proactive location-aware recommendations
- MVP: Post-visit email feedback loop

---

## Architecture at a Glance

```
┌─────────────────────────────────────────────────────────┐
│                     Mobile App                          │
│              React Native (iOS + Android)               │
└────────────────────────┬────────────────────────────────┘
                         │ REST / GraphQL
┌────────────────────────▼────────────────────────────────┐
│                   API Backend                           │
│              Python (FastAPI) or Node.js                │
│   Auth │ Calendar │ Recommendations │ User Profiles     │
└──┬─────────────┬────────────────┬───────────────────────┘
   │             │                │
   ▼             ▼                ▼
PostgreSQL    Redis           Claude API
+ PostGIS   (sessions,      (recommendations,
(user data,  cache)          content generation)
 content DB)
   │
   ▼
Content Pipeline (async workers)
   ├── Google Places API
   ├── Foursquare API
   ├── OpenStreetMap (Overpass)
   ├── Eventbrite API
   └── Wikipedia / Wikidata
```

> Full stack decisions pending PR-140.

---

## Content Lifecycle at a Glance

```
Third-party APIs ──► Generation Job ──► DRAFT
                                           │
                              User visits + reviews
                                           │
                                        ACTIVE ──► Stale timer ──► STALE
                                           │                          │
                                    User flags                   AI refresh
                                           │                          │
                                       DISPUTED               ┌───────┴──────┐
                                           │               ACTIVE        DEPRECATED
                                     AI/manual review           │
                                           │               ARCHIVED
                              ┌────────────┴──────────┐
                           ACTIVE              DEPRECATED ──► ARCHIVED
```

> Full detail in `content-pipeline.md`.

---

## How the 6 Core Mechanics Fit Together

```
1. USER PASSES NEAR KILKENNY
         │
         ▼
2. CONTENT PIPELINE has "Kyteler's Inn" (Active, quality level 4)
         │
         ▼
3. APP SURFACES RECOMMENDATION
   "Kyteler's Inn — oldest pub in Ireland, 5 mins away. Send you an invite?"
         │
    User taps YES
         │
         ▼
4. CALENDAR INVITE SENT (.ics via email)
   "Kyteler's Inn — pub visit", 3pm–4pm, address + notes
         │
    User accepts invite in their calendar app
         │
    4pm passes
         │
         ▼
5. POST-VISIT EMAIL
   "Did you make it to Kyteler's Inn? Quick review?"
         │
    User leaves 4-star review + note
         │
         ▼
6. REPUTATION +12
   Content quality level: Draft → Community Confirmed
   User review saved to platform
   If they create a trail: Creator journey begins
```

---

## Document Versions

| Document | Current Version |
|---|---|
| PR-133-review.md | 1.0 (static — point-in-time review) |
| business-plan.md | 1.5 |
| profile-and-goal-examples.md | 3.1 |
| content-pipeline.md | 1.0 |
| dating-matching.md | 1.0 |
| GUIDE.md | 1.1 |
