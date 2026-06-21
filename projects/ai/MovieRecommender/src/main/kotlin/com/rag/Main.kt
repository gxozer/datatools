package com.rag

import com.rag.ollama.OllamaClient
import com.rag.rag.RagEngine
import com.rag.store.HttpQdrantClient
import com.rag.store.InMemoryVectorStore
import com.rag.store.QdrantVectorStore
import com.rag.store.VectorStore
import kotlinx.coroutines.runBlocking

/**
 * Entry point for the RAG Recommendation Assistant.
 *
 * ## Startup sequence
 * 1. Creates an [OllamaClient] pointed at the local Ollama server.
 * 2. Creates a [VectorStore] and indexes all items from [KnowledgeBase] — this
 *    embeds each item's text and stores the resulting vector. Takes ~5–10 seconds
 *    on first run while the embedding model loads into memory.
 * 3. Drops into an interactive REPL where the user types natural-language queries
 *    and receives personalised recommendations.
 *
 * ## Prerequisites
 * ```bash
 * ollama serve                      # Ollama must be running
 * ollama pull nomic-embed-text      # embedding model
 * ollama pull phi3                  # chat/generation model
 * ```
 *
 * ## Running
 * ```bash
 * ./gradlew run --console=plain
 * ```
 */
fun main() = runBlocking {
    printBanner()

    // ── Wire up components ─────────────────────────────────────────────────
    // To use different models, change the constructor arguments here.
    // Any model available via `ollama list` can be used.
    val ollama = OllamaClient(
        baseUrl    = "http://localhost:11434",
        embedModel = "nomic-embed-text",   // ollama pull nomic-embed-text
        chatModel  = "phi3"                // ollama pull phi3
    )

    val store: VectorStore = when (System.getenv("VECTOR_STORE")?.lowercase()) {
        "qdrant" -> {
            println("Vector store: Qdrant (persistent) — make sure `docker compose up -d` is running\n")
            QdrantVectorStore(embed = { ollama.embed(it) }, qdrant = HttpQdrantClient())
        }
        else -> {
            println("Vector store: in-memory (default) — set VECTOR_STORE=qdrant for persistence\n")
            InMemoryVectorStore(ollama)
        }
    }
    val engine = RagEngine(store, ollama, topK = 4)

    // ── Index the knowledge base ───────────────────────────────────────────
    // Each item is embedded once at startup. In a production system you'd
    // persist these vectors to disk and only re-embed when the catalogue changes.
    try {
        store.indexAll(KnowledgeBase.items)
    } catch (e: Exception) {
        println("\n❌ Failed to build the vector index: ${e.message}")
        println("   Make sure Ollama is running and the required models are pulled:")
        println("     ollama serve")
        println("     ollama pull nomic-embed-text")
        println("     ollama pull phi3")
        if (System.getenv("VECTOR_STORE")?.lowercase() == "qdrant") {
            println("   Using Qdrant — also check that Docker is running:")
            println("     docker compose up -d")
            println("     curl http://localhost:6333/collections   # should return a result")
        }
        store.close()
        ollama.close()
        return@runBlocking
    }

    // ── Interactive REPL ───────────────────────────────────────────────────
    println("Ask me for a movie or book recommendation. Type 'quit' to exit.\n")
    printExamples()

    while (true) {
        print("You: ")
        val input = readlnOrNull()?.trim() ?: break
        if (input.lowercase() in listOf("quit", "exit", "q")) break
        if (input.isBlank()) continue

        println("\n🔍 Retrieving relevant items…")
        val result = try {
            engine.recommend(input)
        } catch (e: Exception) {
            println("\n⚠️  Something went wrong while generating a recommendation: ${e.message}")
            println("   Check that the Ollama server is still running, then try again.\n")
            continue
        }

        // Show retrieved candidates — useful for understanding why results came back
        println("\n📚 Top ${result.retrieved.size} retrieved candidates:")
        result.retrieved.forEach { (item, score) ->
            println("   [${"%3.0f".format(score * 100)}%] ${item.type.label}: ${item.title} (${item.year})")
        }

        println("\n🤖 Assistant:\n")
        println(result.answer)
        println("\n${"─".repeat(60)}\n")
    }

    store.close()
    ollama.close()
    println("Goodbye!")
}

/** Prints the welcome banner. */
private fun printBanner() {
    println("""
        ╔══════════════════════════════════════════╗
        ║   🎬📚  RAG Recommendation Assistant     ║
        ║   Powered by Ollama + nomic-embed-text   ║
        ╚══════════════════════════════════════════╝
    """.trimIndent())
    println()
}

/**
 * Prints a handful of example queries to help the user get started.
 * These are designed to exercise different aspects of the knowledge base
 * — mood, genre, format (movie vs book), themes, and style.
 */
private fun printExamples() {
    println("Example queries:")
    listOf(
        "Something mind-bending and complex, like a puzzle",
        "A feel-good movie for a cozy Sunday afternoon",
        "Hard sci-fi books with lots of physics and space",
        "Something funny but also emotional",
        "A dark thriller with a shocking twist ending",
        "Books with beautiful writing and slow-burn storytelling"
    ).forEach { println("  • $it") }
    println()
}
