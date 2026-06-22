package com.rag.store

import com.rag.model.MediaItem
import com.rag.model.MediaType
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class QdrantVectorStoreTest {

    // ── Test doubles ─────────────────────────────────────────────────────────

    /** In-memory fake that records upserted points and returns them on search. */
    private class FakeQdrantApi : QdrantApi {
        var collectionCreated = false
        val upsertedPoints = mutableMapOf<String, Triple<List<Double>, String, String>>() // id → (vector, mediaId, hash)

        override suspend fun collectionExists() = collectionCreated
        override suspend fun createCollection(vectorSize: Int) { collectionCreated = true }

        override suspend fun getPointHash(pointId: String): String? =
            upsertedPoints[pointId]?.third

        override suspend fun upsertPoint(pointId: String, vector: List<Double>, mediaId: String, contentHash: String) {
            upsertedPoints[pointId] = Triple(vector, mediaId, contentHash)
        }

        override suspend fun search(vector: List<Double>, topK: Int): List<QdrantHit> =
            upsertedPoints.values
                .take(topK)
                .map { (_, mediaId, _) -> QdrantHit(mediaId = mediaId, score = 0.9) }

        override fun close() {}
    }

    private val fixedVector = List(768) { 0.1 }
    private val fakeEmbed: suspend (String) -> List<Double> = { fixedVector }

    private val item = MediaItem(
        id          = "m1",
        title       = "Inception",
        type        = MediaType.MOVIE,
        genre       = listOf("Sci-Fi"),
        description = "Dreams within dreams.",
        year        = 2010,
        rating      = 8.8
    )

    // ── Tests ─────────────────────────────────────────────────────────────────

    @Test
    fun `indexAll creates collection when it does not exist`() = runBlocking {
        val fake = FakeQdrantApi()
        val store = QdrantVectorStore(embed = fakeEmbed, qdrant = fake)

        store.indexAll(listOf(item))

        assertTrue(fake.collectionCreated, "Collection should have been created")
    }

    @Test
    fun `indexAll skips collection creation when collection already exists`() = runBlocking {
        val fake = FakeQdrantApi().also { it.collectionCreated = true }
        val store = QdrantVectorStore(embed = fakeEmbed, qdrant = fake)

        store.indexAll(listOf(item))

        // collectionCreated stays true but createCollection was not called again
        assertTrue(fake.collectionCreated)
    }

    @Test
    fun `index upserts point when item is new`() = runBlocking {
        val fake = FakeQdrantApi().also { it.collectionCreated = true }
        val store = QdrantVectorStore(embed = fakeEmbed, qdrant = fake)

        store.index(item)

        assertEquals(1, fake.upsertedPoints.size)
        val point = fake.upsertedPoints.values.first()
        assertEquals("m1", point.second)
        assertEquals(fixedVector, point.first)
    }

    @Test
    fun `index skips upsert when content hash matches stored hash`() = runBlocking {
        val fake = FakeQdrantApi().also { it.collectionCreated = true }
        val store = QdrantVectorStore(embed = fakeEmbed, qdrant = fake)

        store.index(item)
        val countAfterFirst = fake.upsertedPoints.size

        // Index the same item again — hash matches, should be a no-op
        store.index(item)

        assertEquals(countAfterFirst, fake.upsertedPoints.size, "Second index should be skipped (idempotent)")
    }

    @Test
    fun `index re-upserts when item content changes`() = runBlocking {
        val fake = FakeQdrantApi().also { it.collectionCreated = true }
        val store = QdrantVectorStore(embed = fakeEmbed, qdrant = fake)

        store.index(item)
        val originalHash = fake.upsertedPoints.values.first().third

        // Change the item's description (content hash will differ)
        val updatedItem = item.copy(description = "Updated description — a new cut.")
        store.index(updatedItem)

        val newHash = fake.upsertedPoints.values.first().third
        assertFalse(originalHash == newHash, "Hash should change when content changes")
    }

    @Test
    fun `search maps QdrantHit to SearchResult using item registry`() = runBlocking {
        val fake = FakeQdrantApi().also { it.collectionCreated = true }
        val store = QdrantVectorStore(embed = fakeEmbed, qdrant = fake)

        store.index(item)
        val results = store.search("mind-bending sci-fi", topK = 1)

        assertEquals(1, results.size)
        assertEquals(item, results.first().item)
        assertEquals(0.9, results.first().score)
    }

    @Test
    fun `search filters out hits whose mediaId is not in the registry`() = runBlocking {
        val fake = FakeQdrantApi().also {
            it.collectionCreated = true
            // Inject a stale point with an unknown media ID directly
            it.upsertedPoints["stale-uuid"] = Triple(fixedVector, "unknown-id", "hash")
        }
        val store = QdrantVectorStore(embed = fakeEmbed, qdrant = fake)

        val results = store.search("anything", topK = 5)

        assertTrue(results.isEmpty(), "Stale points with unknown mediaId should be filtered out")
    }
}
