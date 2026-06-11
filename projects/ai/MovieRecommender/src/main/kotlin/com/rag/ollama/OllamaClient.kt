package com.rag.ollama

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ── Request / Response shapes ──────────────────────────────────────────────

/**
 * Request body for `POST /api/embeddings`.
 *
 * @property model  Name of the Ollama embedding model (e.g. "nomic-embed-text").
 * @property prompt The text to embed.
 */
@Serializable
data class EmbedRequest(
    val model: String,
    val prompt: String
)

/**
 * Response from `POST /api/embeddings`.
 *
 * @property embedding Dense float vector. For nomic-embed-text this is 768 dimensions.
 */
@Serializable
data class EmbedResponse(
    val embedding: List<Double>
)

/**
 * A single message in a chat conversation.
 *
 * @property role    One of "system", "user", or "assistant".
 * @property content The message text.
 */
@Serializable
data class ChatMessage(
    val role: String,
    val content: String
)

/**
 * Request body for `POST /api/chat`.
 *
 * @property model    Name of the Ollama chat model (e.g. "llama3", "mistral").
 * @property messages Full conversation history, ordered oldest → newest.
 * @property stream   If true, responses are streamed as newline-delimited JSON.
 *                    This prototype sets it to false for simplicity.
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

/**
 * Response from `POST /api/chat` when [ChatRequest.stream] is false.
 *
 * @property message The assistant's reply.
 * @property done    Always true for non-streaming responses.
 */
@Serializable
data class ChatResponse(
    val message: ChatMessage,
    val done: Boolean
)

// ── Client ─────────────────────────────────────────────────────────────────

/**
 * Thin Ktor-based HTTP client for the Ollama REST API.
 *
 * Exposes two operations used by the RAG pipeline:
 * - [embed]  — converts text to a dense vector (for indexing + retrieval)
 * - [chat]   — generates a response given a conversation history (for generation)
 *
 * ## Setup
 * Ollama must be running before making any calls:
 * ```bash
 * ollama serve               # starts the server on port 11434
 * ollama pull nomic-embed-text
 * ollama pull phi3
 * ```
 *
 * ## Swapping models
 * Pass different model names to the constructor — any model available via
 * `ollama list` will work. For embeddings, `nomic-embed-text` and `mxbai-embed-large`
 * are good choices. For chat, `phi3` and `mistral` are lighter alternatives to
 * heavier models like `llama3` on memory-constrained machines.
 *
 * @param baseUrl    Base URL of the Ollama server (default: http://localhost:11434).
 * @param embedModel Model to use for [embed] calls.
 * @param chatModel  Model to use for [chat] calls.
 */
class OllamaClient(
    private val baseUrl: String = "http://localhost:11434",
    private val embedModel: String = "nomic-embed-text",
    private val chatModel: String = "phi3"
) {
    private val httpInitJson = Json {
        ignoreUnknownKeys = true
        isLenient = true
        // Without this, ChatRequest.stream = false (the Kotlin default) is omitted
        // from the request body, Ollama then defaults to streaming, and returns
        // application/x-ndjson instead of application/json.
        encodeDefaults = true
    }

    private val http = HttpClient(CIO) {
        install(ContentNegotiation) { json(httpInitJson) }
        engine {
            // Generous timeout — local CPU inference can take minutes
            // on memory-constrained machines (e.g. 8GB Macs under memory pressure).
            requestTimeout = 300_000
        }
    }

    /**
     * Converts [text] into a dense embedding vector using [embedModel].
     *
     * Calls `POST /api/embeddings`. The first call after a model pull or
     * server restart will be slow (~5–10s) as the model loads into memory;
     * subsequent calls are fast.
     *
     * @param text Any string — a document chunk, a query, a sentence.
     * @return A list of doubles representing the embedding (768 dims for nomic-embed-text).
     */
    suspend fun embed(text: String): List<Double> {
        val response: EmbedResponse = http.post("$baseUrl/api/embeddings") {
            contentType(ContentType.Application.Json)
            setBody(EmbedRequest(model = embedModel, prompt = text))
        }.body()
        return response.embedding
    }

    /**
     * Sends [messages] to [chatModel] and returns the assistant's reply as a string.
     *
     * Calls `POST /api/chat` with `stream = false`. The full response is returned
     * at once — suitable for short to medium outputs. For long-form or interactive
     * streaming, set `stream = true` and consume the SSE response channel instead.
     *
     * @param messages Complete conversation history. Typically includes a system
     *                 message followed by one or more user turns.
     * @return The assistant's reply text, trimmed of leading/trailing whitespace.
     */
    suspend fun chat(messages: List<ChatMessage>): String {
        val response: ChatResponse = http.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(ChatRequest(model = chatModel, messages = messages, stream = false))
        }.body()
        return response.message.content.trim()
    }

    /** Releases the underlying HTTP client connection pool. Call when shutting down. */
    fun close() = http.close()
}
