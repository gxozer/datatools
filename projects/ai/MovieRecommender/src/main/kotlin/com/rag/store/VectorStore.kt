package com.rag.store

import com.rag.model.EmbeddedChunk
import com.rag.model.MediaItem
import com.rag.model.SearchResult
import com.rag.ollama.OllamaClient
import kotlin.math.sqrt

/**
 * An in-memory vector database for semantic similarity search.
 *
 * ## How it works
 *
 * At startup, every [MediaItem] is converted to a dense float vector ("embedded")
 * by calling [OllamaClient.embed] on [MediaItem.toEmbeddableText]. The resulting
 * [EmbeddedChunk] objects are kept in a plain list.
 *
 * At query time, the user's natural-language query is also embedded, and the store
 * computes the **cosine similarity** between the query vector and every stored vector.
 * The top-K items by similarity score are returned as [SearchResult] objects.
 *
 * ## Cosine similarity
 *
 * ```
 * similarity(A, B) = (A · B) / (|A| × |B|)   ∈ [-1, 1]
 * ```
 *
 * For well-trained embedding models, semantically similar texts produce vectors
 * that point in similar directions, yielding scores close to 1.0. Unrelated texts
 * produce orthogonal vectors (score ≈ 0). Scores above ~0.80 indicate a strong
 * match; below ~0.60 is usually incidental.
 *
 * ## Production upgrade path
 *
 * This in-memory store is fine for hundreds of items. For thousands or millions,
 * replace it with a proper vector database — [Chroma](https://www.trychroma.com/),
 * [pgvector](https://github.com/pgvector/pgvector), or
 * [Qdrant](https://qdrant.tech/). The [com.rag.rag.RagEngine] only calls [search],
 * so swapping stores requires no changes to the RAG logic.
 *
 * @param ollama Client used to embed items and queries.
 */
class VectorStore(private val ollama: OllamaClient) {

    /** All embedded items, populated by [index] / [indexAll]. */
    private val chunks = mutableListOf<EmbeddedChunk>()

    // ── Indexing ────────────────────────────────────────────────────────────

    /**
     * Embeds a single [MediaItem] and stores it in the vector index.
     *
     * Calls [OllamaClient.embed] on [MediaItem.toEmbeddableText], which includes
     * title, year, type, genres, rating, tags, and description — all the fields
     * that help semantic search work well.
     *
     * This is a suspend function because the embedding call is a network request
     * to Ollama. Call it from a coroutine scope or use [indexAll] for batch indexing.
     *
     * @param item The media item to embed and store.
     */
    suspend fun index(item: MediaItem) {
        println("  Indexing: ${item.title}…")
        val vector = ollama.embed(item.toEmbeddableText())
        chunks.add(EmbeddedChunk(item = item, embedding = vector))
    }

    /**
     * Indexes a batch of items, printing progress to stdout.
     *
     * Equivalent to calling [index] for each item sequentially. Processes items
     * one at a time rather than concurrently to avoid overwhelming Ollama.
     *
     * @param items The items to embed and store.
     */
    suspend fun indexAll(items: List<MediaItem>) {
        println("Building vector index for ${items.size} items…")
        items.forEach { index(it) }
        println("✓ Index ready — ${chunks.size} items embedded\n")
    }

    // ── Search ──────────────────────────────────────────────────────────────

    /**
     * Finds the [topK] most semantically similar items to [query].
     *
     * Steps:
     * 1. Embeds [query] using [OllamaClient.embed].
     * 2. Computes cosine similarity between the query vector and every stored vector.
     * 3. Returns results sorted by similarity score descending.
     *
     * @param query Natural-language search query (e.g. "a cozy feel-good movie").
     * @param topK  Maximum number of results to return. Defaults to 5.
     * @return List of [SearchResult] sorted by [SearchResult.score] descending.
     */
    suspend fun search(query: String, topK: Int = 5): List<SearchResult> {
        val queryVec = ollama.embed(query)
        return chunks
            .map { chunk ->
                SearchResult(
                    item  = chunk.item,
                    score = cosineSimilarity(queryVec, chunk.embedding)
                )
            }
            .sortedByDescending { it.score }
            .take(topK)
    }

    /** The number of items currently indexed. */
    val size: Int get() = chunks.size

    // ── Maths ───────────────────────────────────────────────────────────────

    /**
     * Computes the cosine similarity between two vectors [a] and [b].
     *
     * Both vectors must have the same dimension. Returns 0.0 if either
     * vector is the zero vector (avoids division by zero).
     *
     * @throws IllegalArgumentException if [a] and [b] have different sizes.
     */
    internal fun cosineSimilarity(a: List<Double>, b: List<Double>): Double {
        require(a.size == b.size) { "Vector dimensions must match (${a.size} vs ${b.size})" }
        var dot   = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in a.indices) {
            dot   += a[i] * b[i]
            normA += a[i] * a[i]
            normB += b[i] * b[i]
        }
        val denom = sqrt(normA) * sqrt(normB)
        return if (denom == 0.0) 0.0 else dot / denom
    }
}
