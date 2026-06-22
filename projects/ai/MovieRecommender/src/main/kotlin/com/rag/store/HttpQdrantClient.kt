package com.rag.store

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── Private request / response shapes ─────────────────────────────────────

@Serializable private data class CreateCollectionBody(val vectors: VectorConfig)
@Serializable private data class VectorConfig(val size: Int, val distance: String)

@Serializable private data class UpsertPointsBody(val points: List<PointStruct>)
@Serializable private data class PointStruct(
    val id: String,
    val vector: List<Double>,
    val payload: PointPayload
)
@Serializable private data class PointPayload(
    @SerialName("media_id")     val mediaId:     String,
    @SerialName("content_hash") val contentHash: String
)

@Serializable private data class GetPointResponse(val result: PointResult? = null)
@Serializable private data class PointResult(val payload: PointPayload? = null)

@Serializable private data class SearchBody(
    val vector: List<Double>,
    val limit: Int,
    @SerialName("with_payload") val withPayload: Boolean = true
)
@Serializable private data class SearchResponse(val result: List<ScoredPoint>? = null)
@Serializable private data class ScoredPoint(val score: Double, val payload: PointPayload? = null)

// ── Client ─────────────────────────────────────────────────────────────────

/**
 * Ktor-based [QdrantApi] implementation.
 *
 * Uses the same Ktor CIO + kotlinx.serialization pattern as [com.rag.ollama.OllamaClient].
 * All HTTP status codes are checked manually (`expectSuccess = false`) so that 404
 * responses (e.g. point-not-found) can be handled gracefully without exceptions.
 *
 * @param baseUrl        Base URL of the local Qdrant REST API (default: http://localhost:6333).
 * @param collectionName Qdrant collection to use for all operations.
 */
class HttpQdrantClient(
    private val baseUrl: String = "http://localhost:6333",
    private val collectionName: String = "movie_recommender"
) : QdrantApi {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
        engine { requestTimeout = 30_000 }
        // Handle status codes manually; Qdrant 404s should not throw
        expectSuccess = false
    }

    override suspend fun collectionExists(): Boolean {
        val response = http.get("$baseUrl/collections/$collectionName")
        return response.status == HttpStatusCode.OK
    }

    override suspend fun createCollection(vectorSize: Int) {
        val response = http.put("$baseUrl/collections/$collectionName") {
            contentType(ContentType.Application.Json)
            setBody(CreateCollectionBody(vectors = VectorConfig(size = vectorSize, distance = "Cosine")))
        }
        check(response.status.isSuccess()) {
            "Failed to create Qdrant collection '$collectionName': HTTP ${response.status}"
        }
    }

    override suspend fun getPointHash(pointId: String): String? {
        val response = http.get("$baseUrl/collections/$collectionName/points/$pointId")
        if (response.status == HttpStatusCode.NotFound) return null
        check(response.status.isSuccess()) {
            "Failed to fetch point $pointId: HTTP ${response.status}"
        }
        return response.body<GetPointResponse>().result?.payload?.contentHash
    }

    override suspend fun upsertPoint(
        pointId: String,
        vector: List<Double>,
        mediaId: String,
        contentHash: String
    ) {
        val response = http.put("$baseUrl/collections/$collectionName/points") {
            url { parameters.append("wait", "true") }
            contentType(ContentType.Application.Json)
            setBody(
                UpsertPointsBody(
                    points = listOf(
                        PointStruct(
                            id      = pointId,
                            vector  = vector,
                            payload = PointPayload(mediaId = mediaId, contentHash = contentHash)
                        )
                    )
                )
            )
        }
        check(response.status.isSuccess()) {
            "Failed to upsert point $pointId: HTTP ${response.status}"
        }
    }

    override suspend fun search(vector: List<Double>, topK: Int): List<QdrantHit> {
        val response = http.post("$baseUrl/collections/$collectionName/points/search") {
            contentType(ContentType.Application.Json)
            setBody(SearchBody(vector = vector, limit = topK))
        }
        check(response.status.isSuccess()) {
            "Qdrant search failed: HTTP ${response.status}"
        }
        return response.body<SearchResponse>().result.orEmpty().mapNotNull { hit ->
            val mediaId = hit.payload?.mediaId ?: return@mapNotNull null
            QdrantHit(mediaId = mediaId, score = hit.score)
        }
    }

    override fun close() = http.close()
}
