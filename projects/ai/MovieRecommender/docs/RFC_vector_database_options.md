# RFC: Vector Database Options for MovieRecommender

**Status:** Draft
**Date:** 2026-06-15
**Epic:** PR-142
**Related:** PR-143 (implementation), TDS_movie_recommender.md §9

---

## 1. Problem Statement

The current `VectorStore` keeps all embeddings in a `MutableList<EmbeddedChunk>` in JVM heap. Every restart calls `OllamaClient.embed()` for all catalogue items (~5-10s on a cold model). As the catalogue grows this becomes untenable.

PR-143 adds an **opt-in** persistent backend (`VECTOR_STORE=qdrant` activates it; the in-memory store remains the default). This RFC evaluates which vector database best fits the project's constraints before implementation begins.

---

## 2. Requirements

| # | Requirement |
| --- | --- |
| R1 | Local-first: runs entirely on-device, no cloud services |
| R2 | Kotlin/JVM client: prefer REST (Ktor) over JDBC to avoid new driver dependencies |
| R3 | Single Docker container, minimal configuration |
| R4 | Native cosine distance (avoid manual post-processing) |
| R5 | Idempotent upsert: unchanged items must not trigger re-embedding on restart |
| R6 | `VectorStore.search()` contract unchanged — `RagEngine` requires no modifications |
| R7 | Testable without a live instance (interface + fake/stub) |

---

## 3. Options Evaluated

### 3.1 Qdrant

Purpose-built vector search engine written in Rust, with a REST and gRPC API.

**Client:** Native REST API consumed via Ktor — the same pattern as `OllamaClient`. No new dependency class.

**Cosine distance:** Native (`distance: Cosine` in collection config).

**Idempotent upsert:** `PUT /collections/{name}/points` with deterministic point IDs (UUIDv5 of `MediaItem.id`) plus a content-hash payload field. Unchanged items hit the same ID and are not re-sent to Ollama.

**Docker:**
```yaml
services:
  qdrant:
    image: qdrant/qdrant:latest
    ports: ["6333:6333"]
    volumes: ["qdrant_data:/qdrant/storage"]
```

**Pros**
- REST API matches the existing Ktor pattern exactly — same auth/timeout patterns as `OllamaClient`, no new dependency class
- Purpose-built for vector search; cosine distance, payload filtering, and scalar quantisation all native
- Lightweight (~80 MB image, single Rust binary)
- Stable REST API since v1.0; actively maintained

**Cons**
- One more local Docker service to manage alongside Ollama
- REST schema occasionally gains new fields between minor versions (backwards compatible, but worth pinning the image tag)

---

### 3.2 pgvector

PostgreSQL extension that adds a `vector` column type and cosine distance operator (`<=>`).

**Client:** Standard JDBC (`postgresql` driver) + SQL. Requires adding a JDBC dependency to `build.gradle.kts` — a new dependency class not currently in the project.

**Cosine distance:** Via `ORDER BY embedding <=> $queryEmbedding` in SQL.

**Idempotent upsert:** Standard `INSERT ... ON CONFLICT DO UPDATE` keyed on `MediaItem.id`.

**Docker:**
```yaml
services:
  postgres:
    image: pgvector/pgvector:pg16
    environment:
      POSTGRES_PASSWORD: secret
    ports: ["5432:5432"]
    volumes: ["pg_data:/var/lib/postgresql/data"]
```

**Pros**
- Familiar SQL; easy to inspect and debug with `psql`
- Relational queries alongside vector search (useful if metadata filtering is added later)
- PostgreSQL is battle-tested; pgvector is production-grade

**Cons**
- JDBC is inconsistent with the Ktor-based pattern used for Ollama — adds a new dependency class and connection-pool concern
- Coroutine integration requires an async JDBC wrapper (R2DBC or Exposed with coroutine support) — significant added complexity
- Heavier image (~350 MB vs ~80 MB for Qdrant)
- Schema migration boilerplate (`CREATE EXTENSION vector`, DDL for the table)

---

### 3.3 Chroma

Embedding database designed primarily for Python, with a REST API server mode added later.

**Client:** REST API consumable via Ktor. No first-party Kotlin client; all request/response DTOs would be hand-written.

**Cosine distance:** Native (configurable per collection).

**Idempotent upsert:** `POST /api/v1/collections/{id}/upsert` with string IDs.

**Docker:**
```yaml
services:
  chroma:
    image: chromadb/chroma:latest
    ports: ["8000:8000"]
    volumes: ["chroma_data:/chroma/chroma"]
```

**Pros**
- REST API compatible with Ktor
- Simple API surface; popular in Python RAG tutorials (plenty of architectural reference)

**Cons**
- Python-native project — the REST server is a secondary interface; documentation and examples are Python-first, Kotlin support is sparse
- REST API has had breaking changes between minor versions; no stable contract guarantee comparable to Qdrant v1.x
- Less mature server mode than Qdrant (which was designed API-first from the start)

---

### 3.4 Weaviate

Cloud-native vector database with a GraphQL and REST API.

**Client:** REST / GraphQL consumable via Ktor.

**Cosine distance:** Native.

**Docker:** Available, but the default image bundles many optional modules (~1 GB).

**Pros**
- Rich feature set: multi-modal, hybrid search, classification

**Cons**
- Designed for cloud-scale, multi-tenant deployments — significantly over-engineered for a local prototype
- Very large image; complex configuration even for the minimal setup
- GraphQL adds cognitive overhead vs. plain REST for simple upsert + search operations

---

## 4. Comparison Matrix

| Criterion | Qdrant | pgvector | Chroma | Weaviate |
| --- | :---: | :---: | :---: | :---: |
| R1 Local-first | ✅ | ✅ | ✅ | ✅ |
| R2 REST / Ktor (no JDBC) | ✅ | ❌ JDBC | ✅ | ✅ |
| R3 Single container, small image | ✅ ~80 MB | ⚠️ ~350 MB | ✅ | ❌ ~1 GB |
| R4 Native cosine | ✅ | ✅ | ✅ | ✅ |
| R5 Idempotent upsert | ✅ | ✅ | ✅ | ✅ |
| R6 No RagEngine changes | ✅ | ✅ | ✅ | ✅ |
| R7 Testable without live instance | ✅ | ✅ | ✅ | ✅ |
| Kotlin/JVM ecosystem fit | ✅ | ⚠️ JDBC boilerplate | ⚠️ Python-first | ⚠️ |
| API stability | ✅ stable v1 | ✅ | ⚠️ | ✅ |

---

## 5. Recommendation

**Qdrant.**

It is the only option that satisfies all requirements without trade-offs: the REST API matches the existing Ktor pattern exactly (no new dependency class, consistent timeout/auth patterns with `OllamaClient`), the Docker image is smallest, cosine distance is native, the API is stable, and idempotent upsert via deterministic point IDs is straightforward.

**pgvector** is the strongest alternative if the project later needs relational metadata queries alongside vector search, but the JDBC driver and coroutine-compatibility overhead are unjustified for the current use case.

**Chroma** and **Weaviate** are ruled out by API stability concerns and image weight respectively.

---

## 6. Open Questions

1. **Env var design** — `VECTOR_STORE=qdrant` (as in PR-143) vs. `QDRANT_URL=http://localhost:6333` (URL implicitly opts in, and is needed at runtime anyway). Using the URL alone reduces the number of config knobs.
2. **Startup failure mode** — if `VECTOR_STORE=qdrant` is set but Qdrant is unreachable, should the app fail fast with a clear error, or fall back silently to in-memory? (Fail-fast is safer: silent fallback hides misconfiguration.)
3. **Long-term default** — should `InMemoryVectorStore` be retained permanently (zero-config runs + unit tests) or eventually deprecated once Qdrant becomes the recommended path?

---

## 7. References

- [PR-143](https://mgozer.atlassian.net/browse/PR-143) — Add optional persistent vector database backend
- [PR-167](https://mgozer.atlassian.net/browse/PR-167) / `PRD_movie_recommender.md` — §8 Backlog
- [PR-168](https://mgozer.atlassian.net/browse/PR-168) / `TDS_movie_recommender.md` — §9 Planned: Persistent Vector Store
- [Qdrant REST API docs](https://qdrant.tech/documentation/interfaces/rest-api/)
- [pgvector GitHub](https://github.com/pgvector/pgvector)
- [Chroma server mode](https://docs.trychroma.com/production/chroma-server/docker)
