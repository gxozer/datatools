package com.rag.store

import com.rag.model.MediaItem
import com.rag.model.SearchResult
import java.util.UUID

/**
 * Persistent [VectorStore] backed by Qdrant.
 *
 * Activated by setting the `VECTOR_STORE=qdrant` environment variable before running.
 * Qdrant must be running locally — start it with `docker compose up -d`.
 *
 * ## Idempotent indexing
 *
 * Each [MediaItem] is assigned a deterministic point ID (UUID derived from [MediaItem.id])
 * and a content hash derived from [MediaItem.toEmbeddableText]. On startup, items whose
 * hash already matches the stored payload are skipped — no Ollama call, no re-upsert.
 * Only new or changed items trigger embedding.
 *
 * ## Item registry
 *
 * Because Qdrant payloads only store [MediaItem.id], the store maintains an in-memory
 * registry populated during [indexAll]. This registry is used by [search] to reconstruct
 * full [SearchResult] objects. Unknown payload IDs (e.g. stale points from a removed
 * catalogue item) are silently filtered out.
 *
 * @param embed           Suspend function that converts text to an embedding vector.
 *                        In production this is [com.rag.ollama.OllamaClient.embed];
 *                        in tests a simple lambda can be supplied instead.
 * @param qdrant          Qdrant API client. Pass a fake for unit tests.
 * @param collectionName  Qdrant collection to use (default: "movie_recommender").
 * @param vectorSize      Embedding dimensionality — must match the embedding model
 *                        (768 for nomic-embed-text).
 */
class QdrantVectorStore(
    private val embed: suspend (String) -> List<Double>,
    private val qdrant: QdrantApi,
    private val collectionName: String = "movie_recommender",
    private val vectorSize: Int = 768
) : VectorStore {

    /** Maps [MediaItem.id] → [MediaItem]; populated during [indexAll] for use in [search]. */
    private val itemRegistry = mutableMapOf<String, MediaItem>()

    // ── Indexing ────────────────────────────────────────────────────────────

    override suspend fun index(item: MediaItem) {
        itemRegistry[item.id] = item

        val pointId     = pointIdFor(item.id)
        val contentHash = item.toEmbeddableText().hashCode().toString()

        // Skip re-embedding if the stored hash already matches
        if (qdrant.getPointHash(pointId) == contentHash) {
            println("  Skipping (cached): ${item.title}")
            return
        }

        println("  Indexing: ${item.title}…")
        val vector = embed(item.toEmbeddableText())
        qdrant.upsertPoint(
            pointId     = pointId,
            vector      = vector,
            mediaId     = item.id,
            contentHash = contentHash
        )
    }

    override suspend fun indexAll(items: List<MediaItem>) {
        if (!qdrant.collectionExists()) {
            println("Creating Qdrant collection '$collectionName'…")
            qdrant.createCollection(vectorSize)
        }

        println("Building Qdrant vector index for ${items.size} items…")
        items.forEach { index(it) }
        println("✓ Qdrant index ready — ${items.size} items processed\n")
    }

    // ── Search ──────────────────────────────────────────────────────────────

    override suspend fun search(query: String, topK: Int): List<SearchResult> {
        val queryVec = embed(query)
        return qdrant.search(queryVec, topK).mapNotNull { hit ->
            val item = itemRegistry[hit.mediaId] ?: return@mapNotNull null
            SearchResult(item = item, score = hit.score)
        }
    }

    // ── Lifecycle ───────────────────────────────────────────────────────────

    override fun close() = qdrant.close()

    // ── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Derives a deterministic UUID from a [MediaItem.id] string (e.g. "m1", "b3").
     * Uses UUID v3 (MD5) so the same item always maps to the same Qdrant point ID.
     */
    private fun pointIdFor(mediaId: String): String =
        UUID.nameUUIDFromBytes(mediaId.toByteArray()).toString()
}
