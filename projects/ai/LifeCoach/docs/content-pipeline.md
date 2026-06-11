# Artificial Intelligence (AI) Content Pipeline

**Epic:** [PR-133](https://mgozer.atlassian.net/browse/PR-133)
**Version:** 1.0
**Date:** 2026-06-05

The content database is the foundation of the Life Coach App's recommendation engine. This document describes how content is generated, maintained, refreshed, and retired — as a continuously operating background system.

---

## 1. Principles

- **The database is never finished.** Content generation and maintenance run continuously as background processes.
- **AI generates the floor, users raise it.** AI-generated content is always the starting point, never the final word. Community confirmation lifts quality.
- **Serve nothing stale.** Content with low freshness confidence is suppressed or flagged before it reaches a user.
- **Never delete, only archive.** Deprecated content is retained in full — reviews, history, and creator attribution intact — for reinstatement, analytics, and creator reputation preservation.
- **Cost-aware generation.** AI generation is triggered on demand (new area, detected gap) and on a scheduled refresh cycle — never speculatively for areas with no user activity.

---

## 2. Content Types

| Type | Description | Examples |
|---|---|---|
| Venue | A named, addressable place | Pub, restaurant, museum, gallery, gym, music venue |
| Trail | An ordered sequence of locations with a narrative | "Maria's Hidden Pubs of Stoneybatter", "Wicklow Mountain Loop" |
| Route | A navigable path between two points | Walking route, cycling route, driving tour |
| Event | A time-bound occurrence at a location | Record fair, street art festival, walking group, park run |
| Area Guide | A narrative overview of a district or town | "Kilkenny's Medieval Quarter", "Shoreditch Street Art District" |
| List | An unordered collection without a prescribed route | "Best pintxos by category in San Sebastián" |

---

## 3. Content States and Lifecycle

Every content item moves through a defined lifecycle.

```
[DRAFT] → [ACTIVE] → [STALE] → [DEPRECATED] → [ARCHIVED]
                         ↑           ↓
                    [DISPUTED] → review → [ACTIVE] or [DEPRECATED]
```

### State Definitions

| State | Description | Served to users? |
|---|---|---|
| **Draft** | AI-generated from third-party sources; not yet user-confirmed | Yes, with "AI-generated" label |
| **Active** | Confirmed by 2+ user visits or reviews; recently accurate | Yes, full confidence |
| **Stale** | No confirmed activity in 90+ days; or change signals detected | Yes, with reduced confidence weighting; refresh queued |
| **Disputed** | 2+ inaccuracy/closure flags within 30 days | Suppressed; human or AI review triggered |
| **Deprecated** | Confirmed permanently closed, removed, or incorrect | No — excluded from all recommendations |
| **Archived** | Historical; once existed but no longer relevant | No — available in user history and creator records only |

---

## 4. Generation Triggers

Content generation is event-driven. A generation job is queued when:

| Trigger | Description |
|---|---|
| **User proximity** | A user enters an area where content density is below threshold (< 5 relevant items within 1km for their goal profile) |
| **New venue detected** | A third-party Application Programming Interface (API) (Google Places, Foursquare, OpenStreetMap (OSM)) returns a venue that has no platform entry |
| **Coverage gap flagged** | Multiple users visit an area but no content was served — signal of a database gap |
| **Scheduled refresh** | A cron job runs nightly, identifying Active content not confirmed in 60 days and Stale content not refreshed in 30 days |
| **Seasonal activation** | Events or venues with known seasonal patterns are re-activated ahead of their season |
| **User area declaration** | A user sets a travel destination in their calendar — the pipeline pre-generates content for that area before they arrive |

---

## 5. Generation Process

### Step 1: Source Aggregation

For each new or refreshed item, the pipeline pulls from available sources:

- **Google Places API** — name, address, category, hours, rating, photos, "permanently closed" status
- **Foursquare Places API** — category tags, tips, check-in data
- **OpenStreetMap (Overpass API)** — type tags, amenity classification, trail data
- **Eventbrite / Meetup API** — events, dates, capacity, recurrence
- **Wikipedia / Wikidata** — historical context, heritage classification, notable facts
- **Web search (selective)** — recent news, reviews, opening/closing announcements for high-value venues

### Step 2: Cross-Reference Validation

Before generation, the pipeline cross-references:
- Does the venue appear in at least 2 sources? (reduces hallucination risk)
- Does the address resolve to a valid location via geocoding?
- Is the venue marked "permanently closed" in any source? → if yes, skip generation; queue for deprecation check

Items that pass validation proceed to generation. Items that fail are held in a `pending_review` queue for manual or AI-assisted resolution.

### Step 3: AI Content Generation

The pipeline calls the AI model with a structured prompt containing:
- All aggregated source data for the venue
- The target goal profiles this content should serve (e.g. pub → Explorer, Foodie, Social)
- Tone guidance for each profile
- Required output fields: description, character tags, "what to know", typical visit duration, best time to visit

**Output structure:**
```json
{
  "name": "The Hut",
  "address": "Manor Street, Stoneybatter, Dublin 7",
  "category": ["pub", "traditional", "local"],
  "description": "A proper Stoneybatter local — dark wood, no screens, the kind of pub that hasn't tried to be anything other than what it is for 60 years.",
  "character_tags": ["traditional", "quiet", "snug", "solo-friendly", "no-sports-tv"],
  "what_to_know": "Quieter Monday–Wednesday. Cash preferred. The snug at the back is the spot.",
  "typical_visit_duration_mins": 45,
  "best_time": "Weekday lunchtime or early evening",
  "goal_profiles": ["Explorer", "Joyful", "Connector"],
  "confidence_score": 0.82,
  "sources": ["google_places", "openstreetmap", "foursquare"],
  "generated_at": "2026-06-05T14:22:00Z",
  "state": "draft"
}
```

### Step 4: Confidence Scoring

Each generated item receives a confidence score (0.0–1.0) based on:
- Number of sources that agree on key fields (name, address, category)
- Recency of source data
- Whether third-party ratings exist (proxy for "this place is real and visited")
- Whether any conflicting signals exist (e.g. "open" in one source, "closed" in another)

| Score range | Action |
|---|---|
| 0.85–1.0 | Publish as Draft immediately |
| 0.65–0.84 | Publish as Draft with lower recommendation weighting |
| 0.40–0.64 | Hold in `pending_review`; do not serve until confirmed |
| < 0.40 | Discard; log for manual review |

### Step 5: Publication

Items above confidence threshold are written to the content database as `Draft`. They are:
- Immediately eligible for recommendation (with "AI-generated" label)
- Queued for community confirmation (flagged in a review backlog)
- Not eligible for featured placement until they reach `Active` state

---

## 6. Freshness Maintenance

### Signals Monitored Continuously

| Signal | Source | Weight |
|---|---|---|
| User visit logged | Post-visit email confirmation | High |
| Positive review submitted | User review system | High |
| Helpfulness vote trend | Community | Medium |
| Third-party "permanently closed" flag | Google Places, Foursquare | Critical — triggers immediate suppression |
| OSM edit (venue removed or type changed) | OpenStreetMap changesets | Medium |
| User "report inaccuracy" flag | In-app reporting | Medium |
| No activity for 90 days | Internal staleness timer | Low — queues refresh, does not suppress |

### Staleness Timer

Every Active item has a `last_confirmed_at` timestamp updated whenever a user visit or positive review is logged. Items not confirmed within:
- **90 days** → status moves to `Stale`; refresh job queued
- **180 days** (Stale, not refreshed) → confidence score halved; recommendation weighting reduced further
- **365 days** (Stale, still unresolved) → flagged for deprecation review

### Scheduled Refresh Jobs

| Job | Frequency | Scope |
|---|---|---|
| Freshness sweep | Nightly | All Active items; identifies items approaching staleness |
| Stale refresh | Nightly | All Stale items; re-pulls third-party data; re-generates if sources updated |
| Deprecation review | Weekly | All Disputed items + items stale >180 days |
| Seasonal activation | 14 days before season | Events and venues with seasonal open/close patterns |
| Coverage gap scan | Weekly | Areas with recent user activity but below content density threshold |
| High-traffic area refresh | Daily | Top 100 most-recommended venues; keep at maximum freshness |

---

## 7. Deprecation and Archiving

### Deprecation Triggers

A content item is automatically moved to `Deprecated` when:
- Google Places or Foursquare returns `permanently_closed: true`
- 3+ user "closed / does not exist" flags within any 30-day window
- AI refresh returns confidence score < 0.40 and no third-party data supports the entry
- A venue's address geocodes to a different business (name mismatch + address match = likely replacement)

### Deprecation Process

1. Item moved to `Deprecated` state immediately on trigger
2. Removed from all recommendation queries
3. Deprecated notice written to the item record with trigger reason and date
4. If item has user reviews: reviewers notified via email — *"[The Hut] has been marked as closed. Your review has been archived. Thank you for your contribution."*
5. Creator attribution preserved — deprecated content still counts toward creator reputation history
6. Item moved to `Archived` after 30-day grace period (allows reinstatement if incorrectly deprecated)

### Reinstatement

A deprecated item can be reinstated when:
- A user reports it as still open and open status is confirmed by a third-party source
- The item was incorrectly deprecated (e.g. address geocoding error)
- A venue reopens under the same name and character

Reinstated items return to `Draft` state and must re-earn `Active` through community confirmation.

---

## 8. User-Generated Content in the Pipeline

User-created trails, lists, and guides participate in the same lifecycle:

- **Published** — equivalent to `Active`; visible to all users
- **Stale** — creator has not updated in 180+ days AND underlying venues have significant deprecations (>30% of listed venues deprecated)
- **Deprecated** — creator has deleted it, or >50% of underlying venues are deprecated and content is no longer coherent
- **Archived** — preserved with full creator attribution and review history

The pipeline automatically notifies creators when underlying venues in their trails are deprecated:
> *"3 venues in your 'Dublin Hidden Pubs' trail have been marked as closed. Would you like to update the trail?"*

Creator reputation is not penalised for venues closing — only for content that is reported as inaccurate by users.

---

## 9. Content Quality Ladder

Every content item has a quality level used to rank recommendations and display confidence indicators.

| Level | Label | Criteria |
|---|---|---|
| 1 | AI Draft | Generated by AI; no user confirmation |
| 2 | AI Reviewed | AI content cross-checked against 3+ sources; confidence ≥ 0.85 |
| 3 | Community Confirmed | 1+ user visit logged with positive post-visit email response |
| 4 | Community Verified | 3+ independent reviews with consistent details; avg rating ≥ 4.0 |
| 5 | Expert Verified | Reviewed by a Trusted Creator (reputation ≥ 1,000 saves) or platform editor |

Levels 1–2 are served with a visible "AI-generated" indicator. Levels 3–5 display no indicator — they are treated as platform-standard.

---

## 10. Content Generation Cost Management

AI generation at scale has real cost. Guardrails:

- **Demand-driven generation only** — no speculative pre-generation for areas without user activity
- **Batch refresh jobs run off-peak** (2am–6am local time for the area's time zone)
- **Deduplication before generation** — if a venue already exists in the database, skip generation; update existing record instead
- **Short-circuit for high-confidence third-party data** — if Google Places provides a high-quality description and the venue is well-known, use structured extraction rather than full generation
- **Generation budget per area** — configurable cap on the number of new items generated per area per day to prevent runaway costs during viral events
- **Cache all third-party API responses** — 24-hour cache on Places/Foursquare data; 7-day cache on OSM data

---

## 11. Operational Ownership

| Responsibility | Owner |
|---|---|
| Pipeline infrastructure and monitoring | Engineering |
| Generation prompt quality and tone | AI/Product |
| Freshness threshold tuning | Data / Product |
| Deprecation review queue (edge cases) | Content Operations |
| Creator notification on deprecations | Product / Engineering |
| Third-party API contracts and rate limits | Engineering / Legal |
| Multi-language content expansion (future) | TBD |

---

## 12. Open Questions

1. What is the acceptable false deprecation rate — how often can a live venue be incorrectly marked closed before users lose trust?
2. Should content generation be triggered in real-time (user arrives in area) or pre-generated when a calendar event is added for that location?
3. How do we handle areas with no third-party data coverage (rural, less-documented regions)?
4. What is the maximum acceptable "AI Draft" ratio in recommendations for a given area? (e.g. if 80% of content is Draft, does that hurt trust?)
5. When do we expand to non-English content, and what changes in the generation pipeline?
6. How do we handle venues that change character but keep the same name (e.g. a traditional pub that becomes a gastropub)?
7. Should creators be able to manually lock their trail content against pipeline deprecation warnings, or should the pipeline always surface these?
