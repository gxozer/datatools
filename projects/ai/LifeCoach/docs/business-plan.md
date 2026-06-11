# Life Coach App — Business Plan

**Version:** 1.5
**Date:** 2026-06-05
**Epic:** [PR-133](https://mgozer.atlassian.net/browse/PR-133)

---

## 1. Executive Summary

The Life Coach App is an artificial intelligence (AI)-powered personal development platform that integrates with users' calendars to deliver context-aware lifestyle recommendations aligned with their personal goals. By combining calendar intelligence, location awareness, and AI-driven coaching, the app nudges users toward more fulfilling daily routines — without requiring manual journaling or planning.

The app serves anyone with a goal — from a retiree wanting to visit every pub in Dublin, to a teenager building fitness habits, to a professional managing stress. Goals are not limited to health or productivity: adventure, creativity, social, cultural, and lifestyle goals are all first-class citizens. The business operates on a freemium Software as a Service (SaaS) model with premium tiers for advanced AI features and social coaching.

---

## 2. Problem Statement

- Personal coaching is expensive ($100–$500/hr) and inaccessible to most people.
- Productivity and wellness apps exist in silos — calendars, fitness trackers, goal apps, and habit trackers don't talk to each other.
- Most people have goals but no system that proactively creates opportunities to pursue them within their existing schedule.
- Generic AI assistants don't understand a user's personal goals or routines deeply enough to give meaningful advice.
- Existing apps are designed almost exclusively for 25–40-year-olds — older adults are underserved despite being one of the fastest-growing smartphone demographics and having more discretionary time and income than any other segment.
- Older adults face a specific gap: plenty of apps for medication reminders or fall detection, almost nothing for living fully and purposefully.
- Dating apps match on photos and self-reported interests — both of which people optimise, not report honestly. No dating platform matches on what people actually do in the real world.

---

## 3. Product Vision

> "An always-on personal coach that fits into your life, not the other way around."

The app learns your schedule, goals, and preferences, then surfaces small actionable suggestions — a nearby coffee shop before a meeting, a 20-minute walk between calls, a mindfulness break before a stressful event — that compound into meaningful lifestyle improvements over time.

### Core Interaction Model

The product is built around five linked mechanics that minimise user effort at every step:

1. **Zero-setup discovery.** The platform maintains a curated content database — pubs, parks, stadiums, trails, restaurants, events, cultural venues — indexed by location and category. When a user is near something relevant to their goals, the app surfaces it without the user having searched or configured anything. If someone drives through Kilkenny, the app already knows about Kyteler's Inn.

2. **One tap → calendar invite.** When a user accepts a recommendation, the app sends a calendar invite to the user's email — with the venue name, location, opening hours, and contextual notes. The user accepts the invite in their own calendar app (Google Calendar, Outlook, Apple Calendar). The app never writes directly to the user's calendar. The accepted invite is the commitment layer.

3. **Post-visit email feedback loop.** After the event time passes, the app sends a short email asking for a rating and optional review. One tap is enough. Text responses are optional but rewarded. The feedback loop closes the cycle and teaches the app what to suggest next.

4. **Reputation system.** Users accumulate a reputation score through consistent, high-quality reviews. Higher reputation unlocks tangible benefits: reduced subscription cost, early feature access, featured creator placement, and visibility as a trusted local voice.

5. **User-generated content with creator economy.** Users can publish their own trails, lists, and guides. Popular content earns its creator free subscription time, featured placement, and eventually revenue share. The platform's best content comes from the people who have actually done the things — not a content team.

6. **AI-generated and maintained content database.** The platform continuously generates, refreshes, and retires content using AI. When users visit an area with thin coverage, the system generates descriptions, character notes, and contextual information for nearby venues automatically. Content that becomes stale, inaccurate, or unpopular is archived or deprecated on an ongoing basis. The database is never finished — it is always live.

---

## 4. Target Market

### Philosophy: Goals Are Universal
The app does not target a single demographic. Anyone with a goal is a potential user. The AI adapts its tone, recommendation style, and content to each user's age, personality, body type, fitness level, and goal type. A 70-year-old pub explorer and a 16-year-old aspiring athlete are both valid, equal users of the platform.

### User Segments

| Segment | Age Range | Example Goals |
|---|---|---|
| Young Explorer | 16–24 | Visit every music venue in the city, build a social life, start a creative hobby |
| Career Builder | 22–35 | Manage stress, build productive habits, network more |
| Health-Focused Adult | 25–50 | Lose weight, train for a race, improve sleep |
| Life Adventurer | Any | Visit all pubs in Dublin, complete a bucket list, explore new cuisines |
| Parent | 28–50 | Find personal time, model healthy habits for kids, manage family schedule |
| Athlete | Any | Performance training, recovery, competition prep |
| Personality-led | Any | Introvert recharging, extrovert socialising, creative expression |
| Newly Independent | 60–75 | Recently retired or widowed — rediscovering identity, purpose, and routine |
| Active Senior | 60–80 | Stay mobile, mentally sharp, socially connected, pursue long-deferred goals |
| Elder Elder | 75–85+ | Memoir writing, gentle daily engagement, family legacy, dignity and independence |

### Personalisation Axes
The app tailors recommendations across multiple dimensions:
- **Age** — intensity, tone, and type of activities vary by life stage
- **Body type & fitness level** — activities are scaled to current ability, not an idealised norm
- **Personality type** — introvert vs. extrovert, spontaneous vs. structured, competitive vs. collaborative
- **Goal category** — health, adventure, social, creative, cultural, professional, spiritual

### Market Size
- Global wellness app market: ~$6.8B (2025), projected $12B+ by 2030
- Personal development app market: ~$3B (2025)
- Lifestyle & adventure app market: growing rapidly with experience-economy trends
- Global 60+ population using smartphones: ~400M and growing rapidly (fastest-growing demographic segment)
- Senior digital health and wellness app market: ~$2.1B (2025), largely untapped by goal-oriented lifestyle apps
- Total Addressable Market (TAM): ~1.5B smartphone users globally who actively pursue personal goals of any kind
- Serviceable Addressable Market (SAM): ~150M users open to an AI-powered coach that adapts to their specific life and goals
- **Older adult opportunity:** Underserved, high-Lifetime Value (LTV), high-loyalty segment. Users 60+ are 3× more likely to retain a subscription they value and have ~2× the discretionary income of 25–35-year-olds.

---

## 5. Core Features

### Minimum Viable Product (MVP) (Phase 1)
| Feature | Description |
|---|---|
| Calendar read | Connect Google Calendar (read-only); read events to identify free gaps and understand user patterns |
| Calendar invite sending | On acceptance of a recommendation, send a calendar invite to the user's email via iCalendar (.ics); user accepts in their own calendar app |
| Curated content database | Pre-loaded database of pubs, parks, trails, venues, restaurants, and events — indexed by location and category |
| Proactive location-aware discovery | Surface relevant content when user is near it, without requiring prior configuration |
| Goal setting | User selects a goal profile (health, calm, athletic, explorer, etc.) |
| AI recommendations | Suggest activities in calendar gaps based on goals, location, and content database |
| Post-visit email feedback | After accepted events pass, send short email requesting rating and optional review |
| User authentication | Email, Google, and Facebook login |
| User profile | View goals, progress metrics, and recommendation history |

### Phase 2
| Feature | Description |
|---|---|
| Reputation system | Score built from review quality, consistency, and helpfulness votes; unlocks benefits at milestones |
| User-generated content | Users create trails, lists, and venue guides; published to the platform |
| Creator benefits | Saves and helpfulness tracking; free subscription credits, featured placement for popular creators |
| Social graph | Follow/friend model with public/private profiles |
| Messaging | Direct messages with friend filter |
| Voice commands | Hands-free interaction for recommendations and confirmations |
| Scoring & leveling | Points, levels, and achievements per goal profile |
| **Dating matching** | Opt-in activity-based matching; identity revealed only after both sides answer each other's questions — **Elite tier only** — see `docs/dating-matching.md` |

### Phase 3
| Feature | Description |
|---|---|
| Creator revenue share | Revenue attribution for Pro subscriptions driven by creator content |
| Additional calendar providers | Outlook, Apple Calendar, others |
| Community challenges | Group goals and leaderboards |
| Coach marketplace | Connect with human coaches for premium guidance |
| Wearable integration | Apple Watch, Fitbit data to inform recommendations |
| Family connection mode | Adult children can share relevant access and receive gentle updates on a parent's activity (with full consent from the older user) |
| Couples mode | Shared recommendations that respect two different health and goal profiles simultaneously |
| Dating: activity-based date suggestions | After a dating match connects, the app suggests shared venues and activities based on both users' profiles |

---

## 5b. Dating & Activity Matching

Full feature specification: `docs/dating-matching.md`

### Concept

The app has richer matching data than any dating platform: where users actually go, what goals they pursue, how often they explore, and what they notice and review. Dating matching uses this behavioural data — not photos or self-reported interests — as the foundation for compatibility.

### The Flow

1. **Opt-in** — dating is off by default; users enable it explicitly
2. **Match detected** — compatibility calculated from shared venues, goal profiles, activity patterns, and user-set filters (age, distance, gender/orientation); both users notified with no identifying information revealed
3. **Questions** — each user sets 2–5 personal questions they want answered before connecting; the interested party answers the match's questions first
4. **Mutual reveal** — when both sides have answered each other's questions, profiles, photos, and messaging unlock simultaneously; if only one side answers within 7 days, the match expires
5. **Messaging** — chat opens with both sets of Questions and Answers (Q&A) visible as context; app suggests shared activity ideas as optional date starters

### Why This Is Different

| Aspect | This app | Traditional dating apps |
|---|---|---|
| Matching basis | Real behaviour (venues visited, goals pursued) | Self-reported interests and photos |
| First impression | Compatibility + shared places | Photo |
| Identity reveal | Earned via mutual Q&A | Immediate |
| Conversation starter | Questions both sides answered | Cold open |
| Date suggestions | Yes — based on both users' real activity data | No |

### Key Design Decisions
- No snap judgements on appearance — photos are revealed only after mutual Q&A
- Questions are chosen by each user — more personal and revealing than a generic profile form
- Filters are bilateral — both users' constraints must be met for a match to form
- Users under 18 are ineligible regardless of filter settings
- All personal information is held until mutual reveal; nothing identifying is visible before

---

## 5a. AI Content Pipeline

The content database is not a static asset — it is a continuously operating system. See `docs/content-pipeline.md` for full technical and operational detail.

### Generation

Content is generated by AI when:
- A user visits or travels through an area where content coverage is thin
- A new venue, event, or trail is detected via third-party Application Programming Interfaces (APIs) (Google Places, Foursquare, OpenStreetMap, Eventbrite) that has no existing platform entry
- A content gap is flagged by user activity (e.g. multiple users visiting an area but no content served)
- A scheduled refresh cycle identifies stale or low-confidence entries

AI generates: venue name, address, category tags, character description, "what to know" notes, typical visit duration, and an initial quality confidence score. All AI-generated content is labelled as such until confirmed by user reviews.

### Content States

| State | Meaning | Action |
|---|---|---|
| **Draft** | AI-generated, not yet user-confirmed | Served with "AI-generated" label; flagged for early community review |
| **Active** | Confirmed by 2+ user visits or reviews; recently accurate | Served normally |
| **Stale** | No confirmed visit in 90+ days; or signals of change detected | Served with reduced confidence; AI refresh queued |
| **Disputed** | 2+ users flagged as inaccurate or closed | Suppressed from recommendations; human or AI review triggered |
| **Deprecated** | Confirmed closed, removed, or permanently inaccurate | Removed from recommendations; retained in archive |
| **Archived** | Historical record; venue existed but no longer active | Not served; accessible in user history and creator records |

### Freshness Signals

The system uses multiple signals to assess content freshness:
- User visit logs and post-visit email responses
- Helpfulness vote trends (declining = staleness indicator)
- Third-party API checks (Google Places "permanently closed", OpenStreetMap edits)
- Seasonal patterns (events, festivals, outdoor venues with closure periods)
- User "report inaccuracy" flags

### Deprecation and Archiving

Content is automatically queued for deprecation when:
- A venue is marked "permanently closed" by a third-party API
- 3 or more users report it as closed or incorrect within 30 days
- No visit has been logged in 18 months and no third-party confirmation exists
- An AI refresh generates a confidence score below threshold

Deprecated content is never deleted — it is archived with its full history, user reviews, and deprecation reason. This preserves the work of creators and reviewers and allows reinstatement if a venue reopens.

### Content Quality Ladder

| Level | Label | How achieved |
|---|---|---|
| 1 | AI Draft | Generated by AI from third-party data |
| 2 | AI Reviewed | AI content re-checked against multiple sources |
| 3 | Community Confirmed | 1+ user visit logged with positive feedback |
| 4 | Community Verified | 3+ independent user reviews with consistent details |
| 5 | Expert Verified | Reviewed by a Trusted Creator or platform editor |

Higher-quality content is ranked higher in recommendations and displayed with a quality indicator.

---

## 6. Goal Profiles

Goal profiles are the heart of personalisation. Users are not forced into a single profile — they can hold multiple active goals simultaneously. The AI prioritises recommendations across their goals based on available calendar gaps, location, energy level, and stated preferences.

### Health & Fitness Profiles

| Profile | Focus | Example Recommendations |
|---|---|---|
| Health Starter | Gentle wellness, building habits | "10-min walk before your 3pm call — you've got 20 mins free" |
| Athletic | Fitness performance, training | "30-min run window before your morning meeting" |
| Pro Athlete | High-performance training & recovery | "45-min recovery window — stretch or ice bath" |
| Body Positive | Movement for joy, not punishment | "Dance class 5 mins from your next meeting — looks fun!" |
| Senior Active | Gentle activity, mobility, social | "Chair yoga at the community centre — 1pm, 10 mins away" |

### Mental & Emotional Profiles

| Profile | Focus | Example Recommendations |
|---|---|---|
| Calm | Stress reduction, mindfulness | "5-min breathing exercise before your back-to-back meetings" |
| Barely Making It | Survival mode, micro-wins | "One small win today: step outside for 5 minutes" |
| Joyful | Happiness, social connection | "Coffee with a friend near your 2pm — you haven't caught up in 3 weeks" |
| Creative | Art, music, writing, making | "45-min free block — that sketch you wanted to start?" |

### Adventure & Lifestyle Profiles

| Profile | Focus | Example Recommendations |
|---|---|---|
| Explorer | Bucket lists, new experiences | "3 pubs from your Dublin list are within walking distance right now" |
| Foodie | Cuisine discovery, local restaurants | "Highly rated Lebanese spot 8 mins from your 1pm — never tried it" |
| Culture Seeker | Museums, theatre, gigs, history | "Free exhibition at the gallery opens today — 30 mins between your calls" |
| Night Owl | Evening social life, events, nightlife | "Jazz night at your saved venue starts at 9 — you're free after 8:30" |
| Nature Lover | Outdoors, parks, hiking, wildlife | "Short forest trail near your afternoon meeting — 20 mins each way" |

### Social & Professional Profiles

| Profile | Focus | Example Recommendations |
|---|---|---|
| Connector | Expanding social circle, relationships | "You haven't messaged Alex in 3 weeks — coffee near your 11am?" |
| Career Builder | Networking, learning, professional growth | "Meetup on your tech stack tonight — 6pm, 2km away" |
| Family First | Quality time with family | "School pickup + ice cream on the way — 30-min gap at 3pm" |

### Older Adult Profiles

This segment requires distinct profiles, tone, and user experience (UX) — not adaptations of younger profiles.

| Profile | Focus | Example Recommendations |
|---|---|---|
| Memoir & Legacy | Writing life stories, passing on memories | "Tuesday afternoon free — you were writing about arriving in Dublin in 1968. Want to continue?" |
| Gentle Active | Low-impact daily movement, mobility | "Botanical gardens — flat paths, 15 mins, free on Tuesdays. Lovely at this time of year." |
| Cognitive Engagement | Learning, mental sharpness, curiosity | "New thing of the week: the geology of Bath. Walking tour Saturday, very slow pace, retired professor." |
| Reconnect | Maintaining family ties and old friendships | "You haven't spoken to your daughter in 9 days. She's 1 hour behind you — your 7pm works well." |
| Discovery Mode | Finding new interests after major life change (retirement, bereavement, divorce) | "Bookbinding taster class Tuesday — 2 hours, €15. I have no idea if you'd like it. Worth finding out." |
| Grandparent Goals | Building dedicated time and memories with grandchildren | "Ciarán — 7 weeks since your last outing. He has a free Saturday in 2 weeks." |
| Cooking & Independence | Learning to cook, eating well, staying self-sufficient | "This week: scrambled eggs. Most underrated thing to cook well. Ready when you are." |

### How Profiles Work
- Users select **one primary profile** and up to **three secondary profiles** on onboarding
- Profiles have **10 levels each** — users progress by completing recommendations and milestones
- The AI **blends** profiles intelligently: an Athletic + Explorer user near the Wicklow mountains gets a trail run suggestion, not a gym recommendation
- Profiles can be **updated at any time** — the app adapts immediately
- **Custom goals** can be added in plain language: "I want to visit all the pubs in Dublin" → the app matches against its content database, tracks completion, and surfaces nearby ones opportunistically based on location and calendar gaps
- **Discovery mode** is available for users who don't know what they want yet — the app suggests low-commitment tasters and builds a picture over time from email feedback responses
- **Profile learning:** the app observes which suggestions get accepted and which post-visit emails get positive responses; it quietly adjusts future recommendations without the user having to reconfigure anything

---

## 7. Business Model

### Freemium SaaS

| Tier | Price | Features |
|---|---|---|
| Free | $0/month | 1 calendar, 3 AI suggestions/day, 1 goal profile, basic progress tracking |
| Pro | $9.99/month | Unlimited suggestions, all goal profiles, social features, voice commands, User-Generated Content (UGC) creation |
| Elite | $24.99/month | Everything in Pro + dating matching, advanced AI coaching, priority recommendations, analytics dashboard, early access, dating boosts |

### Additional Revenue Streams
- **Partnerships:** Local businesses (coffee shops, gyms, parks, pubs) can pay to have verified listings and respond to reviews — similar to TripAdvisor business model, but scoped to app-generated traffic.
- **Sponsored Discovery:** Businesses can sponsor location-triggered recommendations when a user is nearby. Always clearly labelled; never intrusive.
- **Creator Revenue Share (Phase 3):** 20–30% of revenue attributed to a creator's content is shared back to the creator. Builds a self-sustaining content flywheel.
- **Coach Marketplace (Phase 3):** 15–20% commission on human coach bookings.
- **Enterprise/Business-to-Business (B2B):** Employee wellness packages sold to companies.
- **Family Gift Subscriptions:** Adult children purchasing Pro subscriptions for elderly parents — a proven acquisition channel in the senior wellness market (e.g. Audible, Calm). High LTV, low churn.
- **General Practitioner (GP) / Healthcare Referral Programme (Phase 3):** Partner with healthcare providers who recommend the app as part of post-hospital recovery, chronic condition management, or loneliness intervention programmes.
- **Dating Boosts:** One-time purchases ($2.99) available to Elite users to temporarily increase match visibility.

### Projections (Conservative)

| Year | Users | Paid Conversion | Monthly Recurring Revenue (MRR) |
|---|---|---|---|
| Year 1 | 50,000 | 5% (2,500) | ~$25K |
| Year 2 | 250,000 | 8% (20,000) | ~$200K |
| Year 3 | 1,000,000 | 10% (100,000) | ~$1M |

---

## 8. Competitive Landscape

| Competitor | Strength | Gap We Fill |
|---|---|---|
| Headspace / Calm | Meditation focus, strong brand | Not calendar-aware, no goal-to-schedule mapping |
| Notion / Todoist | Task management | No AI coaching, no calendar-triggered nudges |
| Noom | Behavior change, health focus | Single vertical (weight loss), no calendar integration |
| Google Assistant / Siri | Deep calendar access | Generic, not goal-oriented or personal coaching focused |
| Future (human coaching app) | Real human coaches | Expensive, not scalable |
| Tinder / Bumble / Hinge | Large user base, established brand | Photo-first, self-reported interests, no behavioural matching, no shared-activity foundation |
| Hinge (closest model) | Prompt-based Q&A before messaging | Self-reported data only; no real-world behavioural signal; identity still revealed immediately |

**Differentiator:** We are the only app that serves *any* goal — from elite athletic performance to visiting every pub in Dublin — by combining real-time calendar context + a continuously AI-generated and community-refined content database + calendar invite delivery + a post-visit feedback loop + a creator economy + **behavioural dating matching that reveals identity only after mutual Q&A**. The content moat compounds: AI generates the foundation, users confirm and enrich it, creators organise it, and the pipeline keeps it fresh. The dating feature is uniquely enabled by that same behavioural data — no other dating platform has it.

---

## 9. Go-to-Market Strategy

### Phase 1: Beta (Months 1–6)
- Invite-only beta with 500–1,000 users
- Focus on Google Calendar integration only
- Collect Net Promoter Score (NPS), usage data, and qualitative feedback
- Iterate on recommendation quality

### Phase 2: Public Launch (Months 7–12)
- App Store and Google Play launch
- Content marketing: blog, YouTube, TikTok across multiple verticals — fitness, adventure, food, culture, senior wellness
- Influencer partnerships spanning many niches: wellness, travel, food, nightlife, sports, creativity
- Referral program: invite 3 friends → 1 month Pro free
- Community-specific campaigns (e.g. "Complete every pub on this Dublin crawl map")

### Phase 3: Growth (Year 2+)
- Paid social (Instagram, TikTok)
- B2B outreach to HR/wellness teams
- Expand to Outlook and Apple Calendar
- Launch coach marketplace
- **Older adult acquisition channels:** Facebook (dominant 60+ social platform), community centre partnerships, GP surgery waiting room materials, adult children gifting subscriptions
- **Healthcare partnerships:** approach National Health Service (NHS), Health Service Executive (HSE) (Ireland), and similar bodies about social prescribing programmes — GPs recommending the app for loneliness, post-discharge recovery, and mild depression

---

## 10. Technical Architecture (High-Level)

- **Frontend:** React Native (iOS (Apple's mobile operating system) + Android from one codebase)
- **Backend:** Python (FastAPI) or Node.js — to be decided in PR-140
- **AI/ML:** Claude API (Anthropic) for natural language recommendations and content generation; explore fine-tuning on goal profiles
- **Content pipeline:** Async worker queue (Celery / Simple Queue Service (SQS)) processing generation, refresh, and deprecation jobs; separate from the request path
- **Content database:** PostgreSQL with PostGIS for geospatial queries; full-text search via Elasticsearch or pgvector
- **Third-party data feeds:** Google Places API, Foursquare, OpenStreetMap (Overpass API), Eventbrite — for seeding and freshness checks
- **Calendar APIs:** Google Calendar API read-only (MVP), Microsoft Graph API read-only (Phase 2)
- **Invite delivery:** iCalendar (.ics) format sent via email (no calendar write permission required); alternatively Google Calendar invite via API if user grants permission
- **Auth:** OAuth2 (Google, Facebook), email/password via JSON Web Token (JWT)
- **Database:** PostgreSQL (user data, goals), Redis (session/cache)
- **Infrastructure:** AWS or GCP — to be decided in PR-140

> See PR-140 (Tech Stack) and `docs/content-pipeline.md` for final decisions.

---

## 11. Privacy & Compliance

- Calendar and location data classified as sensitive Personally Identifiable Information (PII)
- Data encrypted at rest (Advanced Encryption Standard (AES)-256) and in transit (Transport Layer Security (TLS) 1.3)
- General Data Protection Regulation (GDPR) and California Consumer Privacy Act (CCPA) compliance required at launch
- Users must explicitly consent to location access
- Voice data: processed in real-time, not stored by default
- Privacy policy and data deletion flow required before public launch

---

## 12. Risks & Mitigations

| Risk | Likelihood | Impact | Mitigation |
|---|---|---|---|
| Low recommendation quality | High | High | Start with limited suggestions; gather feedback fast; iterate AI prompts |
| Calendar API rate limits / permissions | Medium | High | Cache aggressively; design for degraded mode without calendar |
| Privacy breach | Low | Critical | Security review before launch; pen test; GDPR compliance |
| Low user retention | High | High | Onboarding flow with quick wins; push notification strategy |
| Competitor (Google/Apple) copies feature | Medium | High | Build social/community moat that big tech won't replicate |
| App store rejection | Low | Medium | Review Apple/Google guidelines early; especially for health data |
| Dating safety incident | Low | Critical | No identity revealed before mutual Q&A; block/report at all stages; contact sharing requires explicit consent from both users; under-18s ineligible |
| Dating feature cannibalising focus | Medium | Medium | Feature is opt-in, off by default, and contained in Phase 2; core coaching product ships and matures first |
| Older adult onboarding drop-off | High | Medium | Simplified UI mode, voice input, family-assisted setup flow, larger text defaults |
| Inappropriate tone for vulnerable users | Medium | High | Distinct AI tone profiles per segment; grief/bereavement handling tested with real users before launch |
| Health data liability (older adults) | Medium | High | Clear terms of service; app is not a medical device; consult legal before healthcare partnership outreach |
| AI-generated content inaccuracy | High | Medium | All AI content labelled as Draft; never served as authoritative until community-confirmed; deprecation pipeline handles corrections |
| Content database costs (AI generation at scale) | Medium | Medium | Generate content on-demand for visited areas only; batch refresh jobs run off-peak; cache aggressively |
| Stale content served to users | Medium | High | Freshness scoring on all content; staleness signals trigger re-generation or suppression before user sees it |
| AI content hallucination (wrong address, closed venue) | Medium | High | Third-party API cross-check before publishing; disputed content suppressed immediately; no AI content served without at least one data source cross-reference |

---

## 13. Milestones

| Milestone | Target Date | Tickets |
|---|---|---|
| Tech stack decided | TBD | PR-140 |
| Development practices defined | TBD | PR-134 |
| RFC: Calendar integration | TBD | To be created |
| RFC: Scoring/leveling system | TBD | To be created |
| MVP backend (auth + calendar + AI) | TBD | To be created |
| MVP frontend | TBD | To be created |
| Private beta launch | TBD | — |
| Public launch | TBD | — |

---

## 14. Open Questions

1. What is the primary target platform for MVP — iOS, Android, or web?
2. Which AI provider do we use for recommendations? (RFC needed)
3. How do we handle offline mode when no internet connection is available?
4. What is the minimum calendar event density needed for meaningful recommendations?
5. How do we prevent the recommendation engine from being annoying/intrusive?
6. How do we source and maintain location databases for adventure/explorer goals (pub lists, restaurants, trails, venues)?
7. How do we handle age-appropriate content filtering — e.g. no nightlife suggestions for under-18 users?
8. How do we personalise for body type and fitness level without making users feel judged during onboarding?
9. Should custom goals (plain-language input) be supported at MVP or Phase 2?
10. How do we handle users with multiple very different goal profiles without overwhelming them with suggestions?
11. What does the simplified UI mode look like for older adults — who designs and tests it, and when does it ship?
12. How does voice transcription work for memoir/journalling features — on-device or cloud? What are the privacy implications?
13. How do we handle family-assisted setup — what permissions does a family member get, and how does the older user retain full control?
14. What is the right tone guide for bereavement and grief scenarios — do we need a clinical psychologist to review AI prompts for this segment?
15. Should we pursue NHS/HSE social prescribing partnerships, and if so, what regulatory or clinical evidence standards would be required?
16. How do we build and maintain the content database at launch — internal curation, third-party data sources (Foursquare, Google Places, OpenStreetMap), or a combination? See `docs/content-pipeline.md`.
17. What is the email feedback loop design — frequency, timing after event, opt-out mechanism, and how responses feed back into the recommendation engine?
18. What is the reputation scoring model — how are reviews weighted, how are helpfulness votes counted, and how do we prevent gaming?
19. At what creator milestone does revenue share kick in, and how is attribution tracked (last-click? assisted? direct save-to-visit flow)?
20. How do we moderate user-generated content for accuracy, tone, and inappropriate material without destroying the creator experience?
21. What is the acceptable AI content error rate before a user loses trust in the platform? How do we measure it?
22. Who owns content pipeline operations — engineering, content, or a dedicated data team?
23. How do we handle content generation for languages other than English — is multi-language support in scope, and when?
