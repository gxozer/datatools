# TDS: MovieRecommender — Local RAG Movie/Book Recommender

**Version:** 1.0
**Date:** 2026-06-15
**Status:** Draft
**Jira:** PR-168
**Epic:** PR-142
**Related:** PRD_movie_recommender.md (sibling, PR-167)

---

## 1. Overview

This document describes the current technical design of the MovieRecommender prototype: a Kotlin/Ktor application implementing a Retrieve → Augment → Generate (RAG) pipeline over a local Ollama server.

---

## 2. Directory Layout

```
MovieRecommender/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
└── src/
    ├── main/kotlin/com/rag/
    │   ├── Main.kt              # Entry point + REPL
    │   ├── KnowledgeBase.kt     # 16-item catalogue
    │   ├── model/Models.kt      # MediaItem, EmbeddedChunk, SearchResult
    │   ├── ollama/OllamaClient.kt
    │   ├── store/VectorStore.kt
    │   └── rag/RagEngine.kt
    └── test/kotlin/com/rag/
        └── store/VectorStoreTest.kt
```

---

## 3. Components

### 3.1 `OllamaClient` (`ollama/OllamaClient.kt`)

Thin Ktor (CIO engine) HTTP client for the local Ollama REST API:

* `embed(text): List<Double>` — `POST /api/embeddings`, returns a 768-dim vector (nomic-embed-text)
* `chat(messages): String` — `POST /api/chat` with `stream = false`
* 300s request timeout (local CPU inference can be slow)
* `close()` releases the connection pool

### 3.2 `VectorStore` (`store/VectorStore.kt`)

In-memory vector index:

* `chunks: MutableList<EmbeddedChunk>`
* `index(item)` / `indexAll(items)` — embed via `OllamaClient.embed`, append to `chunks`
* `search(query, topK = 5)` — embed query, brute-force cosine similarity vs. every chunk, sort descending, take top-K
* `cosineSimilarity(a, b)` — `internal`, unit tested
* `size` — number of indexed items

### 3.3 `RagEngine` (`rag/RagEngine.kt`)

Orchestrates Retrieve → Augment → Generate:

1. **Retrieve** — `vectorStore.search(query, topK)` (default `topK = 4`)
2. **Augment** — formats retrieved items into a context block (title, year, genres, rating, tags, description, similarity score)
3. **Generate** — sends a system + user message to `OllamaClient.chat` (default model `phi3`); the system prompt constrains the model to recommend only from the provided context

Returns `RecommendationResult(query, retrieved, answer)`.

### 3.4 `Models` (`model/Models.kt`)

* `MediaItem` — id, title, type (MOVIE/BOOK), genre, description, year, rating, tags; `toEmbeddableText()` serialises all fields for embedding
* `EmbeddedChunk` — `MediaItem` + 768-dim embedding
* `SearchResult` — `MediaItem` + cosine similarity score

### 3.5 `KnowledgeBase` (`KnowledgeBase.kt`)

Hardcoded list of 16 `MediaItem`s (8 movies, 8 books, ids `m1`-`m8`/`b1`-`b8`).

### 3.6 `Main` (`Main.kt`)

* Constructs `OllamaClient` (`nomic-embed-text` / `phi3`), `VectorStore`, `RagEngine(topK = 4)`
* `indexAll(KnowledgeBase.items)` at startup, wrapped in try/catch with a friendly error pointing at the README's Troubleshooting steps
* Interactive REPL: reads queries, calls `engine.recommend()`, prints retrieved candidates + answer; per-query try/catch so one bad query doesn't kill the session
* `quit` / `exit` / `q` ends the session and calls `ollama.close()`

---

## 4. Build & Run

* Plugins: `kotlin("jvm") 2.0.21`, `kotlin("plugin.serialization") 2.0.21`, `application`
* Dependencies: Ktor client (core/cio/content-negotiation/serialization) 2.3.7, kotlinx.coroutines 1.7.3, kotlinx.serialization.json 1.6.2, Logback 1.4.14
* `jvmTarget = 17`; `application.mainClass = com.rag.MainKt`
* `tasks.named<JavaExec>("run") { standardInput = System.\`in\` }` — required for the REPL to read stdin
* `tasks.test { useJUnitPlatform() }`

Prerequisites: `ollama serve`, `ollama pull nomic-embed-text`, `ollama pull phi3`, Java 17+.

---

## 5. Testing

* `VectorStoreTest` — `cosineSimilarity`: identical vectors → 1.0, orthogonal → 0.0, zero vector → 0.0 (no div-by-zero), mismatched dimensions → `IllegalArgumentException`
* `ModelsTest` — `MediaItem.toEmbeddableText()` formatting, including the empty-tags case

---

## 6. Key Design Decisions

### 6.1 Ktor over other HTTP clients

The CIO engine keeps the dependency footprint small and works well with coroutines; used for Ollama calls today and for the planned Qdrant client (§9).

### 6.2 In-memory `VectorStore` for the prototype

Brute-force cosine similarity over a `MutableList` is simple, dependency-free, and fast enough for a 16-item catalogue. `RagEngine` depends only on `search()`, so the backend can be swapped without touching the RAG pipeline (see §9).

### 6.3 `phi3` as the default chat model

Lightweight, memory-friendly default for local development; swappable for `llama3`/`mistral` via constructor args.

### 6.4 Per-call try/catch in the REPL

A single failed Ollama call (model not loaded, connection drop) should not terminate the whole session — only the in-flight query is reported as failed.

---

## 7. Known Limitations

* Catalogue is hardcoded in `KnowledgeBase.kt` (16 items); no external ingestion.
* `VectorStore` is in-memory — full re-embed (~5-10s) on every restart (see §9).
* No CI configured.

---

## 8. Open Questions

* Which production vector database to standardise on for persistence (see §9 — Qdrant proposed).
* Whether to add a catalogue-ingestion path (JSON/DB) once persistence lands.

---

## 9. Planned: Persistent Vector Store (PR-143)

The in-memory `VectorStore` re-embeds the entire catalogue on every startup and loses its index on exit. PR-143 replaces it with a persistent backend while keeping `VectorStore.search()`'s contract unchanged for `RagEngine`.

**Proposed direction:** **Qdrant**, accessed over its REST API via Ktor — mirroring the existing `OllamaClient` pattern (no new JDBC driver or client library), with native cosine distance and a single `docker-compose` service. Indexing becomes idempotent: a deterministic point ID + content hash per `MediaItem` means unchanged items are not re-embedded on restart. Detailed design (collection schema, point ID/payload scheme, `docker-compose.yml`, file-level changes, and testing strategy) is tracked in PR-143.
