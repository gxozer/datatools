# RFC: External Catalogue Ingestion for MovieRecommender

**Status:** Draft
**Date:** 2026-06-15
**Epic:** PR-142
**Jira:** PR-169
**Related:** PR-143 (Qdrant backend), RFC_vector_database_options.md

---

## 1. Problem Statement

`KnowledgeBase.kt` contains 16 hardcoded `MediaItem` objects. With Qdrant now available as a persistent backend (PR-143), the catalogue can grow well beyond 16 items — but there is no mechanism to discover or ingest new movies or books from the outside world. Adding a new item currently requires editing Kotlin source code and recompiling.

This RFC evaluates external data sources and ingestion strategies so that the catalogue can be kept current without manual code changes.

---

## 2. Requirements

| # | Requirement |
| --- | --- |
| R1 | Local-first: no always-on internet dependency; the REPL must work offline once data is ingested |
| R2 | `MediaItem`-compatible: ingested data must map cleanly to `{id, title, type, genre, description, year, rating, tags}` |
| R3 | Idempotent: re-running ingestion must not re-embed unchanged items (PR-143's content-hash mechanism covers this) |
| R4 | Covers both movies and books |
| R5 | Free or low-cost for personal use |
| R6 | No new build-time dependencies beyond what is already in `build.gradle.kts` (Ktor + kotlinx.serialization) |

---

## 3. External Data Sources

### 3.1 TMDB (The Movie Database)

REST API. Free tier: 1 000 requests/day (enough for any personal use case).

**Data available:** title, year, genres, plot overview, vote average, language, cast, keywords, director (via credits endpoint).

**MediaItem mapping:**

| MediaItem field | TMDB field |
| --- | --- |
| `id` | `"tmdb-{id}"` |
| `title` | `title` |
| `type` | `MOVIE` |
| `genre` | `genres[].name` |
| `description` | `overview` |
| `year` | `release_date[0..3]` |
| `rating` | `vote_average` |
| `tags` | `keywords` (extra API call) or skip |

**API key:** required (free registration at themoviedb.org). Supplied as `TMDB_API_KEY` env var.

**Pros:** comprehensive, well-documented, widely used. Descriptions are included in the base response. Supports queries like "popular", "top rated", "now playing", and keyword search.

**Cons:** movies only; books require a separate source. An extra call is needed for keywords/tags.

---

### 3.2 OMDB (Open Movie Database)

REST API. Free tier: 1 000 requests/day.

**Data available:** Title, Year, Genre, Plot (short), imdbRating, Director, Actors.

**MediaItem mapping:** similar to TMDB. Plot is shorter (~one sentence) than TMDB's overview (~two paragraphs), which makes embeddings less informative.

**API key:** required (free registration).

**Pros:** simpler API — a single request returns all fields including plot and rating.

**Cons:** shorter descriptions hurt embedding quality; narrower catalogue coverage than TMDB; no "discover popular" endpoint (must search by title).

---

### 3.3 Google Books API

REST API. Free, no API key required for read-only public data (subject to quota).

**Data available:** title, authors, categories (genre), description, publishedDate, averageRating, pageCount, language.

**MediaItem mapping:**

| MediaItem field | Google Books field |
| --- | --- |
| `id` | `"gb-{volumeId}"` |
| `title` | `volumeInfo.title` |
| `type` | `BOOK` |
| `genre` | `volumeInfo.categories` |
| `description` | `volumeInfo.description` |
| `year` | `volumeInfo.publishedDate[0..3]` |
| `rating` | `volumeInfo.averageRating` |
| `tags` | `volumeInfo.authors` |

**Pros:** free, no API key, rich description field, good coverage of popular titles. Authors mapped to `tags` works well for embedding (author style is a meaningful search dimension).

**Cons:** `averageRating` is often missing for older or less popular books; `categories` can be coarse (`["Fiction"]` rather than `["Literary Fiction", "Historical"]`).

---

### 3.4 Open Library (Internet Archive)

Free, open REST API. No API key required.

**Data available:** title, authors, subjects (genre-like), first_publish_year, description (inconsistent).

**MediaItem mapping:** similar to Google Books. `subjects` is a list of free-form tags (e.g. `["Fiction", "London (England)", "19th century"]`) — richer as embedding material but harder to normalise.

**Pros:** completely open data; very large catalogue; subjects make good embedding tags.

**Cons:** descriptions are often absent or very short; `averageRating` is not available; inconsistent data quality across editions.

---

### 3.5 IMDb Datasets (bulk TSV)

Free bulk downloads (tsv.gz). Updated daily. Covers millions of titles.

**Data available (without descriptions):** primaryTitle, startYear, genres, averageRating, numVotes, titleType.

**Pros:** most comprehensive title coverage; no API rate limits; genres and ratings are reliable.

**Cons:** **no plot descriptions** in the free dataset — every `MediaItem` would need a separate OMDB/TMDB API call to get a `description`, making bulk ingestion expensive. Large files (title.basics.tsv.gz is ~900 MB). Requires a local processing pipeline rather than a simple HTTP call.

---

## 4. Comparison Matrix

| Criterion | TMDB | OMDB | Google Books | Open Library | IMDb Datasets |
| --- | :---: | :---: | :---: | :---: | :---: |
| R1 Local-first (no always-on) | ✅ | ✅ | ✅ | ✅ | ✅ |
| R2 MediaItem mapping | ✅ | ✅ | ✅ | ⚠️ (no rating) | ⚠️ (no description) |
| R3 Idempotent (via PR-143) | ✅ | ✅ | ✅ | ✅ | ✅ |
| R4 Movies | ✅ | ✅ | ❌ | ❌ | ✅ |
| R4 Books | ❌ | ❌ | ✅ | ✅ | ❌ |
| R5 Free for personal use | ✅ | ✅ | ✅ | ✅ | ✅ |
| R6 No new dependencies | ✅ (Ktor) | ✅ (Ktor) | ✅ (Ktor) | ✅ (Ktor) | ❌ (file I/O pipeline) |
| Description quality | ✅ | ⚠️ (short) | ✅ | ⚠️ (inconsistent) | ❌ (absent) |
| API key required | ✅ | ✅ | ❌ | ❌ | ❌ |
| "Discover popular" query | ✅ | ❌ | ✅ | ⚠️ | ✅ (bulk) |

---

## 5. Sync Strategies

### 5.1 On-demand CLI command

A standalone ingestion entrypoint (e.g. `IngestMain.kt`) invoked separately from the REPL:

```bash
TMDB_API_KEY=xxx ./gradlew ingest --args="--source=tmdb --query=popular --limit=50"
```

Fetches items from the configured source, converts to `MediaItem`, and upserts into Qdrant via `QdrantVectorStore`. The REPL is not involved and does not need internet access at runtime.

**Pros:** clean separation of concerns; REPL stays fully offline after ingestion; aligns with local-first principle; user controls when the catalogue is refreshed.

**Cons:** manual; stale until the user reruns; requires remembering the command.

---

### 5.2 Startup refresh (bounded)

On REPL startup, fetch the top-N popular items from configured sources before entering the query loop. PR-143's content-hash idempotency means only genuinely new/changed items trigger Ollama embed calls.

```
[startup] Fetching top 20 popular movies from TMDB…  (3 new, 17 cached)
[startup] Fetching top 10 popular books from Google Books… (1 new, 9 cached)
```

**Pros:** catalogue always current; zero extra commands for the user.

**Cons:** requires internet on every startup (violates R1 for offline use); adds startup latency (~1–2s for the HTTP fetch even when everything is cached); Ollama must be running, adding pressure to start two services before the fetch.

---

### 5.3 Scheduled background sync

A background coroutine (or OS cron job) periodically fetches new items and upserts into Qdrant while the REPL runs.

**Pros:** fully transparent to the user.

**Cons:** significantly more complex; requires the REPL process to stay alive for updates to accumulate; overkill for a local prototype.

---

## 6. Catalogue Management

Currently `Main.kt` calls `store.indexAll(KnowledgeBase.items)` at startup, which re-indexes the 16 hardcoded items every time (in-memory store) or skips them via hash check (Qdrant store).

### Option A — Keep KnowledgeBase as a seed layer, ingest on top

`KnowledgeBase.items` continues to be indexed at startup (zero-config, always available offline). Ingested external items are added to Qdrant alongside them, identified by their source prefix (`tmdb-*`, `gb-*`).

**Pros:** backward compatible; app works with no API keys and no internet; curated seed provides a known-good baseline.

**Cons:** KnowledgeBase can become stale relative to ingested data; two sources of truth to maintain.

### Option B — Replace KnowledgeBase with a local JSON catalogue file

`catalogue.json` replaces `KnowledgeBase.kt`. Ingestion appends to this file; the app reads it at startup and indexes it via Qdrant.

**Pros:** no recompilation to add items; single source of truth; human-readable.

**Cons:** another file to manage; needs a read step before indexing; breaks the current zero-config startup.

### Option C — Qdrant as the sole source of truth; KnowledgeBase removed

Ingestion populates Qdrant directly. The REPL starts, assumes Qdrant is populated, and searches without any `indexAll` call.

**Pros:** clean separation; startup is instant (no embedding at all).

**Cons:** requires a separate ingestion run before the REPL will return any results; Qdrant must always be running; breaks zero-config dev experience.

---

## 7. Recommendation

### Sources

| Media type | Recommended source | Alternative |
| --- | --- | --- |
| Movies | **TMDB API** | OMDB (simpler but shorter descriptions) |
| Books | **Google Books API** | Open Library (richer tags, weaker descriptions) |

TMDB and Google Books together cover both media types with high-quality description fields, are free, and consume Ktor (no new dependencies). IMDb Datasets and Open Library are ruled out as primary sources by missing descriptions and inconsistent quality respectively.

### Sync strategy

**On-demand CLI command (Option 5.1).**

This is the only option that fully satisfies R1 (local-first). The REPL remains offline-capable after initial ingestion; the user controls freshness. A bounded startup refresh (5.2) can be reconsidered once the core ingestion pipeline is stable.

### Catalogue management

**Option A — Keep KnowledgeBase as seed, ingest on top.**

Zero-config startup preserved; ingested items augment the known-good baseline. Items sourced from TMDB/Google Books use prefixed IDs (`tmdb-*`, `gb-*`) so they are unambiguously distinguishable from hand-curated seed items.

---

## 8. Proposed Implementation Sketch

```
src/main/kotlin/com/rag/
├── ingestion/
│   ├── Ingester.kt          # interface: suspend fun ingest(limit: Int): List<MediaItem>
│   ├── TmdbIngester.kt      # TMDB implementation (requires TMDB_API_KEY env var)
│   ├── GoogleBooksIngester.kt
│   └── IngestMain.kt        # standalone entrypoint: reads env vars, calls ingester, upserts to Qdrant
```

`IngestMain.kt` wires: `OllamaClient → QdrantVectorStore → ingester.ingest(limit) → store.indexAll(items)`.

`build.gradle.kts` gains a second `application` entry or a custom Gradle task (`./gradlew ingest`) pointing at `IngestMain.kt`.

---

## 9. Open Questions

1. **API key distribution**: TMDB requires `TMDB_API_KEY`. Should the README document this, or should the app fail with a clear message when the key is absent?
2. **Limit**: how many items to fetch per run? (Suggested default: 50 movies from TMDB, 30 books from Google Books.)
3. **Item removal**: if a TMDB title is deleted or its ID changes, should the corresponding Qdrant point be pruned? (Not needed for prototype; can be addressed later.)
4. **KnowledgeBase lifetime**: once external ingestion is working well, should `KnowledgeBase.kt` be deprecated in favour of a richer seed file?
5. **Rate limiting**: TMDB's 1 000 req/day free tier supports ~50 items with credits (3 calls/item: details + keywords + credits). Is that acceptable?

---

## 10. References

- [PR-169](https://mgozer.atlassian.net/browse/PR-169) — this ticket
- [PR-143](https://mgozer.atlassian.net/browse/PR-143) — Qdrant optional backend (prerequisite)
- [PR-142](https://mgozer.atlassian.net/browse/PR-142) — MovieRecommender epic
- [TMDB API docs](https://developer.themoviedb.org/docs)
- [Google Books API docs](https://developers.google.com/books/docs/v1/using)
- [Open Library API](https://openlibrary.org/developers/api)
- [OMDB API](https://www.omdbapi.com/)
