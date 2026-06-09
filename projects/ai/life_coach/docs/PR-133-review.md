# PR-133: Life Coach App (Epic) — Review

**Ticket:** [PR-133](https://mgozer.atlassian.net/browse/PR-133)
**Type:** Epic
**Project:** Prototypes
**Status:** To Do
**Reviewed:** 2026-06-05

---

## Overview

This is a product Epic defining a life coach app that integrates calendar management with artificial intelligence (AI)-powered activity recommendations, social features, voice commands, and a gamification/scoring system. It sits in the **Prototypes** project and currently has two child tasks: PR-134 (Development Practices) and PR-140 (Tech Stack), both still in **To Do**.

---

## Strengths

- **Clear product vision** — the core value proposition (AI suggestions tied to your calendar and goals) is well-articulated.
- **Request for Comments (RFC)-first approach** — explicitly calling for RFCs on calendar integration and the scoring/leveling system before committing to a direction is good practice.
- **Social engagement loop** — follow/friend/message model with public/private profile controls is coherent and well-thought-out.

---

## Issues & Gaps

### 1. No Acceptance Criteria / Definition of Done
The Epic has no acceptance criteria. What does "done" look like for this epic? Without this, there's no way to know when it can be closed.

### 2. RFC Tickets Are Missing
The description calls for RFCs on:
- Calendar integration (Google, Outlook, other providers)
- Scoring/leveling system

Neither RFC ticket exists as a child issue — only PR-134 and PR-140 are linked. These RFC stories should be created and tracked explicitly.

### 3. No Feature Breakdown Into Stories
The epic mixes vision, requirements, and implementation notes in one blob of text. Each major feature area needs its own story/ticket:
- Calendar integration
- AI recommendation engine
- Authentication (email, Facebook, Google)
- User profiles & goal tracking
- Social graph (follow/friend/message)
- Voice commands
- Scoring & leveling system

### 4. No Prioritization / Minimum Viable Product (MVP) Definition
All features are listed at the same level with no indication of what's MVP vs. phase 2. Calendar integration + AI recommendations is the core differentiator — everything else (social, voice, scoring) should be explicitly marked as later phases.

### 5. AI Requirements Are Under-specified
"Explore which AI products to use" needs to be its own spike or RFC ticket. The description should clarify at minimum: real-time vs. batch recommendations, on-device vs. cloud inference, and any latency/cost constraints.

### 6. Privacy & Security Not Addressed
- Calendar data is highly sensitive personal data — no mention of data handling, encryption, or storage policy.
- Voice commands imply microphone access and potentially voice data storage — needs a data retention policy.
- Social messaging opens phishing/spam vectors — moderation approach not mentioned.
- No mention of General Data Protection Regulation (GDPR)/California Consumer Privacy Act (CCPA) compliance, which is critical given calendar + location + personal goals data.

### 7. No Non-Functional Requirements
Missing:
- Target platforms (iOS (Apple's mobile operating system)? Android? Web?)
- Offline support expectations
- Scalability / user count targets
- Availability SLA

### 8. Goal Profiles Need Definition
"barely making it, joyful, calm, health, athletic, pro athlete" — these are interesting but entirely undefined. How do they affect recommendations? What levels exist per profile? This needs a dedicated RFC or design doc before stories can be written.

---

## Recommendations

| Priority | Action |
|---|---|
| High | Add acceptance criteria to the epic |
| High | Create RFC tickets for calendar integration and scoring/leveling |
| High | Break the epic into child stories per feature area |
| High | Define MVP scope explicitly |
| Medium | Add a spike ticket for AI provider evaluation |
| Medium | Add a story for privacy/compliance requirements |
| Low | Define non-functional requirements (platforms, scale, SLA) |

---

**Overall:** The epic captures a genuinely interesting product idea but is not yet ready to drive sprint work. It needs to be decomposed into stories with clear acceptance criteria before development can begin in earnest.
