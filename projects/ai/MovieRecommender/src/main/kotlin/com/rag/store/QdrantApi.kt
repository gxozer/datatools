package com.rag.store

/**
 * A single search hit returned by [QdrantApi.search].
 *
 * @property mediaId     The [com.rag.model.MediaItem.id] stored in the point's payload.
 * @property score       Cosine similarity score returned by Qdrant (0–1).
 */
data class QdrantHit(val mediaId: String, val score: Double)

/**
 * Minimal Qdrant REST operations needed by [QdrantVectorStore].
 *
 * Keeping this as an interface lets tests inject a [FakeQdrantApi] without
 * requiring a running Qdrant instance.
 */
interface QdrantApi {
    /** Returns true if the collection already exists. */
    suspend fun collectionExists(): Boolean

    /** Creates the collection with the given vector dimensionality and cosine distance. */
    suspend fun createCollection(vectorSize: Int)

    /**
     * Returns the `content_hash` stored in the payload of [pointId], or null if the
     * point does not exist. Used to decide whether re-embedding is necessary.
     */
    suspend fun getPointHash(pointId: String): String?

    /**
     * Upserts a single vector point. If a point with [pointId] already exists it is
     * overwritten. The payload stores [mediaId] and [contentHash] for idempotency checks.
     */
    suspend fun upsertPoint(pointId: String, vector: List<Double>, mediaId: String, contentHash: String)

    /**
     * Performs a cosine-similarity search and returns the top-[topK] hits, each
     * carrying the `media_id` from the point payload.
     */
    suspend fun search(vector: List<Double>, topK: Int): List<QdrantHit>

    /** Releases any underlying HTTP client resources. */
    fun close()
}
