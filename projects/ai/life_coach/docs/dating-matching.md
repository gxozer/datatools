# Dating & Activity Matching — Feature Spec

**Epic:** [PR-133](https://mgozer.atlassian.net/browse/PR-133)
**Version:** 1.0
**Date:** 2026-06-05

---

## 1. Concept

Most dating apps match on photos and self-reported interests. People lie, or at least optimise, for a profile. This system matches on **what users actually do** — the venues they visit, the goals they pursue, the trails they complete, the content they create. The result is compatibility rooted in real behaviour, not a curated self-image.

Identity is revealed gradually and only when both sides have earned it through conversation. There are no cold swipes. No snap judgements on appearance. The first thing you know about a match is what they care about — not what they look like.

---

## 2. Why This Works Here

The Life Coach App has behavioural data that no dating app has:

- **Where users actually go** — venues visited, confirmed via calendar and post-visit email
- **What they care about** — goal profiles actively selected and pursued
- **How they engage with life** — activity frequency, exploration radius, consistency
- **What they notice** — trails and lists they've created, reviews they've written
- **Their neighbourhood patterns** — where they spend time regularly

Two people who have both visited 12 pubs in Stoneybatter, both follow the "Explorer" and "Joyful" profiles, and both write long reviews have far more in common than two people who both wrote "loves travel and food" on a dating profile.

---

## 3. The Matching Flow

### Step 1: Opt-In

Dating matching is available exclusively to **Elite tier** subscribers and is **off by default**. Users explicitly enable it in settings. Enabling it does not change anything visible in the rest of the app — it simply activates the matching engine for their profile. A user who downgrades from Elite to Pro has their matching paused and pending matches held for 30 days in case they upgrade again.

---

### Step 2: Match Calculation

The matching engine runs in the background. When two opted-in users exceed a compatibility threshold, a match is queued.

**Compatibility is calculated from:**

| Signal | Weight |
|---|---|
| Overlapping goal profiles (e.g. both "Explorer" + "Joyful") | High |
| Shared venues visited (independently, not together) | High |
| Similar exploration radius and activity frequency | Medium |
| Overlapping saved trails or content | Medium |
| Same neighbourhood or frequent areas | Medium |
| Review tone and character similarity (artificial intelligence (AI)-analysed) | Low |
| Activity recency (both active in last 30 days) | Required — inactive users not matched |

**Filters applied before matching:**
- Age range (user-set)
- Maximum distance (user-set)
- Gender / orientation preferences (user-set)
- Goal profile overlap minimum (at least 1 shared active profile required)
- Mutual opt-in (both users must have dating enabled)

A match is only surfaced when the compatibility score meets the threshold **and** all filters pass on both sides. Filters are always bilateral — if one user sets "within 10km" and the other sets "within 5km", both constraints apply.

---

### Step 3: The Match Notification

When a match is found, both users receive a notification. Nothing is revealed — no name, no photo, no profile.

> **"You have a new match."**
> *Someone with similar interests and places you visit. Tap to find out more.*

The notification shows only:
- A compatibility indicator (e.g. shared goal profiles as icons: 🧭 Explorer, ☕ Joyful)
- The number of places you've both visited (without naming them)
- How recently they were active

Nothing else. The user decides whether to engage.

---

### Step 4: Questions

If the user taps "I'm interested", they see the **questions their match has written** for anyone who wants to connect with them. These are personal, open-ended questions the match chose — not a generic profile form.

Examples of questions users might set:
- *"What's the last place in this city that genuinely surprised you?"*
- *"What are you working on right now that you're proud of?"*
- *"Best meal you've had in the last 6 months — where and what?"*
- *"If you had a free afternoon with no plans, where would you end up?"*
- *"What's something you've been doing lately that you'd recommend to a stranger?"*

The user answers these questions. Their answers are held — not shown to the match yet.

The match simultaneously sees their own notification, and if they also tap "I'm interested", they see **the other user's questions** and answer them.

---

### Step 5: Mutual Answer Reveal

When **both sides have answered each other's questions**, the reveal unlocks:
- Full profile (name, bio, goal profiles, review count, reputation level)
- Photos (minimum 1 required to use the dating feature; maximum 6)
- The ability to send a first message

The first message is optional — but the answers to each other's questions are both visible, giving a natural starting point for conversation.

**If only one side answers:** nothing is revealed. The answered questions are held for up to **7 days**. If the other side doesn't answer within 7 days, the match expires silently. No notification of expiry — it simply disappears from the match queue.

**If neither side engages within 7 days:** the match expires.

---

### Step 6: Messaging

Once both sides have answered, a private chat opens. The first view of the chat shows:
- Both sets of questions and answers, side by side
- The option to send a first message

Messaging is standard: text, optional photo sharing, read receipts (optional). No external links, phone numbers, or social handles can be sent until both users explicitly share them (the app prompts both sides to confirm before a contact exchange is processed).

---

### Step 7: Activity-Based Date Suggestions

After a match unlocks messaging, the app can suggest shared activities based on **both users' profiles combined**:

> *"You've both visited 8 pubs in the Liberties. The Brazen Head is on both your lists and neither of you has been. First date territory?"*

> *"You're both 'Nature Lover' profile. Glendalough is a 45-min drive and neither of you has been. Clear Saturday morning."*

These are suggestions only — opt-in, never pushy.

---

## 4. Profile Setup for Dating

Users who enable dating matching complete a short dating profile in addition to their main profile:

| Field | Required | Notes |
|---|---|---|
| Photos | Yes (min 1, max 6) | Not shown until mutual answer reveal |
| Short bio | Optional | Free text, max 300 chars |
| Questions for matches | Required (min 2, max 5) | What you want to know before connecting |
| Age range filter | Required | Min/max age they want matched with |
| Distance filter | Required | Max distance from current location |
| Gender / orientation | Required | Who they want to be matched with |
| Show me to | Required | Who can match with them (gender/orientation) |
| Profile visibility | Toggle | Pause matching without disabling feature |

Questions are the most important field. Users who spend time on thoughtful questions attract better responses and have more successful matches. The app offers prompt suggestions if users are stuck.

---

## 5. Privacy and Safety

### What is never revealed before mutual answer:
- Name
- Photos
- Exact location or home neighbourhood
- Social media handles
- Any identifying information

### What is visible before mutual answer:
- Compatibility score and shared goal profile icons
- Number of shared venues visited (count only, not names)
- General activity level and recency

### Safety features:
- **Block** available at any stage, including before mutual answer
- **Report** available at any stage with categorised reasons
- **Pause matching** — stops new matches without disabling the feature or losing existing connections
- **Contact sharing consent** — the app prompts both sides before phone numbers or social handles can be exchanged in chat
- Users under 18 are ineligible for the dating feature regardless of age filter settings

### Data handling:
- Dating profile data stored separately from main app data
- Disabling dating feature permanently deletes all pending matches and dating profile data within 24 hours
- Messaging history retained for 90 days after a conversation is ended; user can delete earlier

---

## 6. How Matches Are Surfaced — Examples

### Example A: Maria and Ciarán (Dublin)

Maria (34, pub crawler, Explorer + Joyful profiles) and Ciarán (31, loves live music and local history, Explorer + Culture Seeker profiles) have both:
- Visited 14 of the same Dublin pubs independently
- Both reviewed Kehoe's on South Anne Street within 3 weeks of each other
- Both follow "Explorer" and have it as their primary profile
- Both have active dating matching enabled, age filters compatible, within 4km

Match notification to both:
> *"You have a new match. You've both visited 14 of the same places. Explorer and Joyful in common."*

Ciarán's questions: *"Best pub you've found in the last month that most people don't know about?"* / *"What's the story you always tell at parties about something that happened in Dublin?"*

Maria answers both. She's good at this.

Ciarán sees the notification, taps interest, answers Maria's questions: *"What's the last place in this city that genuinely surprised you?"* / *"If you had a free Friday afternoon, where would you end up?"*

Both answer. Profiles unlock. Ciarán's first message: *"The story about the lock-in at The Cobblestone — I need to hear the rest of that."*

---

### Example B: Sam and Hana (Amsterdam)

Sam (29, introvert, board games, sci-fi, software developer) and Hana (27, developer, sci-fi, same book club) have both:
- Attended the same board game café 3 times each
- Attended the same sci-fi book club (they've been in the same room twice without connecting)
- Both have "Creative" and "Connector" profiles active
- Both opted into dating, filters compatible

Match notification:
> *"You have a new match. You've both visited 3 of the same places. Creative and Connector in common."*

Hana's questions: *"What book changed the way you think about something?"* / *"What's a small thing you do that makes your week better?"*

Sam sees the questions and immediately knows this is going to be a good conversation.

---

### Example C: Nina and Erik (Helsinki)

Nina (33, winter swimmer, PE teacher) and Erik (35, marathon runner, outdoor enthusiast) have both:
- Swum at Lauttasaari sea pool in winter
- Both have "Athletic" + "Nature Lover" profiles
- Both reviewed the same running trail in Nuuksio National Park

Match notification:
> *"You have a new match. You've both visited 5 of the same places. Athletic and Nature Lover in common."*

Erik's questions include: *"Coldest you've ever swum in, and did you go back?"*

Nina: *"0.8°C. Obviously."*

---

## 7. Competitive Differentiation

| Feature | This App | Tinder / Bumble | Hinge | Feeld |
|---|---|---|---|---|
| Matching basis | Real behaviour | Photos + self-report | Self-report | Self-report |
| First impression | Compatibility + shared places | Photo | Photo | Photo |
| Identity reveal | Earned via mutual Questions and Answers (Q&A) | Immediate | Immediate | Immediate |
| Conversation starter | Questions both sides answered | Cold open | Prompt-based | Cold open |
| Date suggestion engine | Yes (based on both profiles) | No | No | No |
| Organic connection via shared places | Yes | No | No | No |

---

## 8. Monetisation

| Feature | Tier |
|---|---|
| Dating opt-in and basic matching | Elite ($24.99/month) — required |
| Unlimited active matches | Elite |
| See who answered your questions (without answering theirs) | Elite |
| Boost — temporarily increase match visibility | One-time purchase ($2.99, Elite only) |
| Extend match expiry from 7 to 14 days | Elite |
| See shared venues and trails before mutual answer | Elite |

Dating is exclusive to the Elite tier. It is not available on Free or Pro. The feature requires an active goal profile and calendar connection — both of which Elite users will have — and the exclusivity reinforces the Elite tier's value proposition as the premium experience.

---

## 9. Open Questions

1. Should both users see the match at the same time, or should one see it first and decide before the other is notified?
2. How many active matches can a user have simultaneously? Is there a cap?
3. Should the app show a "reason for match" — e.g. "You've both been to 14 of the same pubs" — or keep it abstract?
4. What happens when a match is made between users who have already met in real life (same book club, same walking group)? Is that a problem or a feature?
5. How do we handle low-activity areas where there are very few opted-in users — do we widen filters automatically, or just surface fewer matches?
6. Should the Q&A be visible after a match ends (by choice or expiry)?
7. How do we handle the edge case where a user creates very identifying questions ("What do you look like?", "What's your last name?")? Question moderation policy needed.
8. Is there a minimum reputation score or activity level required to use dating matching — to prevent inactive or low-trust accounts from matching?
