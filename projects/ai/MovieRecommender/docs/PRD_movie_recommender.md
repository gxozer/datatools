# PRD: MovieRecommender — Local RAG Movie/Book Recommender

**Version:** 1.0
**Date:** 2026-06-15
**Status:** Draft
**Jira:** PR-167
**Epic:** PR-142
**Related:** TDS_movie_recommender.md (sibling, PR-168)

---

## 1. Purpose

MovieRecommender is a fully local, privacy-friendly Retrieval-Augmented Generation (RAG) prototype that recommends movies and books from a curated catalogue based on a user's natural-language description of what they're in the mood for — with no cloud APIs and no data leaving the machine.

---

## 2. Background

Plain LLM recommendations are limited to the model's training data, can hallucinate titles, and can't be updated without retraining or fine-tuning. MovieRecommender instead retrieves the most relevant items from a curated, embeddable catalogue and grounds the LLM's response in those real items, with similarity scores shown for transparency.

| Plain LLM | RAG (this project) |
| --- | --- |
| Recommends from training data only | Recommends from *your* curated catalogue |
| Can hallucinate titles or details | Every recommendation is grounded in real items |
| Can't be updated without retraining | Add new items to `KnowledgeBase.kt` instantly |
| No source attribution | Shows which items were retrieved + similarity scores |

The prototype was recently restructured to match its documented Gradle layout, gained error handling around Ollama calls, and got its first unit tests (`VectorStore.cosineSimilarity`, `MediaItem.toEmbeddableText`).

---

## 3. Goals

* Given a natural-language request (mood, genre, theme), return the best 1-3 catalogue matches with an explanation of why each fits.
* Ground every recommendation in the curated catalogue — never invent titles.
* Run entirely on-device: embedding and chat models served locally via Ollama.
* Make the catalogue easy to extend (add a `MediaItem`, re-run, done).
* Provide a simple interactive REPL for exploration and demos.

---

## 4. Scope

### In scope (current prototype)

* Interactive CLI/REPL (`Main.kt`)
* Hardcoded 16-item catalogue of movies & books (`KnowledgeBase`)
* Text embedding via Ollama (`nomic-embed-text`)
* Vector similarity search (`VectorStore`, in-memory)
* Recommendation generation via Ollama chat model (`RagEngine`, default `phi3`)
* Friendly error handling when Ollama is unreachable or a model isn't pulled
* Unit tests for pure logic: cosine similarity, embeddable-text formatting

### Out of scope (tracked separately)

* Persistent vector storage — **PR-143**
* CI pipeline
* Web/GUI front-end
* Multi-user support, authentication
* Catalogue ingestion from external sources (JSON/DB)

---

## 5. Functional Requirements

| # | Requirement |
| --- | --- |
| FR1 | User can type a natural-language query describing mood/genre/theme and receive a recommendation. |
| FR2 | The system retrieves the top-K (default 4) most semantically similar catalogue items via embedding + cosine similarity. |
| FR3 | The system shows the retrieved candidates and their similarity scores before the final answer. |
| FR4 | The generated recommendation picks 1-3 items from the retrieved candidates, explains why each fits, and notes caveats. |
| FR5 | The system never recommends items outside the provided catalogue context. |
| FR6 | Adding a new `MediaItem` to `KnowledgeBase` and restarting indexes it automatically — no other code changes needed. |

---

## 6. Non-Functional Requirements

* **Local-first / privacy:** all inference (embedding + chat) happens via a local Ollama server; no cloud API calls.
* **Resilience:** Ollama connectivity issues (server down, model not pulled) produce a friendly message, not a raw stack trace; a single failed query doesn't crash the REPL session.
* **Platform:** Java 17+, Kotlin 2.0.21, runs via `./gradlew run --console=plain`.
* **Extensibility:** swapping the embedding/chat model or the vector store backend requires no changes to `RagEngine`.

---

## 7. Success Criteria

* [ ] `./gradlew run --console=plain` builds the index and starts the REPL.
* [ ] Each of the README's example queries returns a relevant, grounded recommendation.
* [ ] `./gradlew build` compiles and all unit tests pass.
* [ ] README accurately documents setup, architecture, and configuration.

---

## 8. Known Follow-ups / Backlog

| Ticket | Description |
| --- | --- |
| PR-143 | Replace the in-memory `VectorStore` with a persistent vector database (embeddings survive restarts; see TDS §9 "Planned: Persistent Vector Store"). |
| _unfiled_ | Add a CI pipeline (`./gradlew build` on push/PR). |
