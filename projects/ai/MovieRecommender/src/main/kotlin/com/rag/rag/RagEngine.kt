package com.rag.rag

import com.rag.model.SearchResult
import com.rag.ollama.ChatMessage
import com.rag.ollama.OllamaClient
import com.rag.store.VectorStore

/**
 * Orchestrates the three-step RAG pipeline: **Retrieve → Augment → Generate**.
 *
 * ## The pipeline in detail
 *
 * ### Step 1 — Retrieve
 * The user's natural-language [query] is embedded by [VectorStore.search], which
 * returns the [topK] most semantically similar [com.rag.model.MediaItem] objects
 * from the knowledge base, each with a cosine similarity score.
 *
 * ### Step 2 — Augment
 * The retrieved items are formatted into a structured context block — a block of
 * text containing each item's title, year, genres, rating, tags, and description.
 * This context is injected directly into the LLM prompt so the model has real,
 * grounded facts to work from.
 *
 * ### Step 3 — Generate
 * A two-message conversation (system + user) is sent to [OllamaClient.chat]. The
 * system message tells the model its role and rules (only recommend from the
 * provided context, explain why each match fits, etc.). The user message contains
 * both the original query and the context block.
 *
 * ## Why this produces better results than plain LLM
 *
 * Without retrieval, the LLM would answer from its training data, which may be
 * outdated, incomplete, or hallucinated. By grounding the prompt in retrieved
 * chunks, the model is constrained to items that actually exist in your catalogue,
 * and the similarity scores give it a strong prior on which items are most relevant.
 *
 * @param vectorStore The indexed knowledge base to retrieve from.
 * @param ollama      Client used for the generation step.
 * @param topK        Number of candidates to retrieve per query (default 4).
 *                    Higher values give the LLM more to choose from but increase
 *                    prompt length and latency.
 */
class RagEngine(
    private val vectorStore: VectorStore,
    private val ollama: OllamaClient,
    private val topK: Int = 4
) {
    /**
     * Produces a personalised recommendation for the given [query].
     *
     * This is the main entry point for the RAG pipeline. It is a suspend function
     * because both the retrieval step (embedding the query) and the generation step
     * (calling the chat model) are async network calls to Ollama.
     *
     * @param query A natural-language request, e.g. "something funny but emotional".
     * @return A [RecommendationResult] containing the original query, the retrieved
     *         candidates with their similarity scores, and the LLM's final answer.
     */
    suspend fun recommend(query: String): RecommendationResult {

        // ── Step 1: Retrieve ───────────────────────────────────────────────
        val results = vectorStore.search(query, topK = topK)

        // ── Step 2: Augment — build the context block ──────────────────────
        val context = buildContextBlock(results)

        // ── Step 3: Generate ───────────────────────────────────────────────
        val messages = listOf(
            ChatMessage(
                role = "system",
                content = """
                    You are a friendly and knowledgeable media recommendation assistant.
                    You help people find movies and books they'll love.
                    
                    You will be given a user's request and a list of candidate items retrieved
                    from a knowledge base. Your job is to:
                    1. Pick the BEST 1-3 matches from the candidates
                    2. Explain WHY each match fits the user's request
                    3. Highlight any caveats (e.g. "if you don't mind slow pacing…")
                    4. Keep the tone warm, concise, and enthusiastic
                    
                    Only recommend items from the provided context — do not invent titles.
                """.trimIndent()
            ),
            ChatMessage(
                role = "user",
                content = """
                    User request: "$query"
                    
                    Candidate items from knowledge base:
                    $context
                    
                    Please give a personalised recommendation based on these candidates.
                """.trimIndent()
            )
        )

        val answer = ollama.chat(messages)
        return RecommendationResult(query = query, retrieved = results, answer = answer)
    }

    /**
     * Formats a list of [SearchResult] objects into a plain-text context block
     * that is injected into the LLM prompt.
     *
     * Each entry includes the similarity score so the model can, in principle,
     * weight higher-scoring items more strongly. In practice the instruction
     * prompt does this implicitly.
     *
     * Example output for a single item:
     * ```
     * [Movie] Inception (2010) — similarity: 0.94
     * Genres: Sci-Fi, Thriller
     * Rating: 8.8/10
     * Tags: mind-bending, heist, dreams, complex-plot, Christopher Nolan
     * Description: A thief who steals corporate secrets…
     * ```
     */
    private fun buildContextBlock(results: List<SearchResult>): String =
        results.joinToString("\n\n") { (item, score) ->
            """
            [${item.type.label}] ${item.title} (${item.year}) — similarity: ${"%.2f".format(score)}
            Genres: ${item.genre.joinToString(", ")}
            Rating: ${item.rating}/10
            Tags: ${item.tags.joinToString(", ")}
            Description: ${item.description}
            """.trimIndent()
        }
}

/**
 * The output of a single [RagEngine.recommend] call.
 *
 * @property query     The original user query.
 * @property retrieved The candidates retrieved from the vector store, in order
 *                     of descending similarity score.
 * @property answer    The final natural-language recommendation from the LLM.
 */
data class RecommendationResult(
    val query: String,
    val retrieved: List<SearchResult>,
    val answer: String
)
