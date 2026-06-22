package com.rag.store

import com.rag.model.MediaItem
import com.rag.model.SearchResult

/**
 * Common contract for all vector index backends.
 *
 * Two implementations ship out of the box:
 * - [InMemoryVectorStore] — default, zero-config (re-embeds the whole catalogue on every restart)
 * - [QdrantVectorStore] — persistent, activated by setting `VECTOR_STORE=qdrant`
 *
 * [com.rag.rag.RagEngine] only calls [search], so the active backend is fully transparent to it.
 */
interface VectorStore {
    /** Embeds [item] and adds it to the index. */
    suspend fun index(item: MediaItem)

    /** Indexes all [items], ensuring the collection/index exists first. */
    suspend fun indexAll(items: List<MediaItem>)

    /**
     * Returns the [topK] catalogue items most semantically similar to [query].
     * Results are sorted by cosine similarity score descending.
     */
    suspend fun search(query: String, topK: Int = 5): List<SearchResult>

    /** Releases any resources held by this store (e.g. HTTP client connections). No-op by default. */
    fun close() {}
}
