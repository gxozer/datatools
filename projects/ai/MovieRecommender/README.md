# 🎬📚 RAG Recommendation Assistant

> A fully local, privacy-friendly movie & book recommendation engine built with **Kotlin + Ollama**.
> No cloud APIs. No data leaves your machine.

---

## What is this?

This is a working **Retrieval-Augmented Generation (RAG)** prototype. You describe what you're in the mood for in plain English, and the assistant finds the best matches from its knowledge base and explains *why* they fit your request.

```
"Something mind-bending with a shocking twist"
        │
        ▼
  [Embed your query]  ←  nomic-embed-text (local)
        │
        ▼
  [Cosine similarity search]  ←  in-memory vector store
        │
        ▼
  Top 4 matching movies/books
        │
        ▼
  [Inject into prompt + generate]  ←  phi3 (local)
        │
        ▼
  "I'd recommend Oldboy (2003) — a Korean neo-noir
   that delivers one of cinema's most shocking reveals…"
```

**Why RAG instead of just asking an LLM directly?**

| Plain LLM | RAG |
|---|---|
| Recommends from training data only | Recommends from *your* curated catalogue |
| Can hallucinate titles or details | Every recommendation is grounded in real items |
| Can't be updated without retraining | Add new items to `KnowledgeBase.kt` instantly |
| No source attribution | Shows you exactly which items were retrieved + similarity scores |

---

## Prerequisites

### 1. Install Ollama

Ollama runs LLMs locally on your machine.

```bash
# macOS
brew install ollama

# Linux
curl -fsSL https://ollama.com/install.sh | sh

# Windows — download installer from https://ollama.com/download
```

### 2. Pull the two required models

```bash
ollama pull nomic-embed-text   # ~274 MB — converts text to vectors
ollama pull phi3               # ~2.2 GB — generates the recommendation text
```

> **Tip:** `nomic-embed-text` is a dedicated embedding model — fast and lightweight.
> `phi3` is a lightweight chat model that's friendlier to memory-constrained
> machines. You can swap it for `llama3`, `mistral`, or any chat model you
> already have pulled.

### 3. Start the Ollama server

```bash
ollama serve
# Listening on http://localhost:11434
```

Keep this running in a separate terminal while you use the app.

### 4. Java 17+

```bash
java -version   # must be 17 or higher
```

---

## Quick start

```bash
git clone <this-repo>
cd rag-recommender
./gradlew run --console=plain
```

On first run, Gradle downloads dependencies (~30 seconds). Then the app embeds all 16 items in the knowledge base (~5–10 seconds depending on your machine), and drops you into the interactive REPL.

---

## Example session

```
╔══════════════════════════════════════════╗
║   🎬📚  RAG Recommendation Assistant     ║
║   Powered by Ollama + nomic-embed-text   ║
╚══════════════════════════════════════════╝

Building vector index for 16 items…
  Indexing: Inception…
  Indexing: Interstellar…
  … (16 items total)
✓ Index ready — 16 items embedded

Ask me for a movie or book recommendation. Type 'quit' to exit.

Example queries:
  • Something mind-bending and complex, like a puzzle
  • A feel-good movie for a cozy Sunday afternoon
  • Hard sci-fi books with lots of physics and space
  • Something funny but also emotional
  • A dark thriller with a shocking twist ending
  • Books with beautiful writing and slow-burn storytelling

You: hard sci-fi books that feel optimistic and fun

🔍 Retrieving relevant items…

📚 Top 4 retrieved candidates:
   [ 96%] Book: Project Hail Mary (2021)
   [ 89%] Book: The Hitchhiker's Guide to the Galaxy (1979)
   [ 81%] Book: The Three-Body Problem (2008)
   [ 74%] Book: Dune (1965)

🤖 Assistant:

For hard sci-fi that's also genuinely fun, **Project Hail Mary** by Andy Weir is
your perfect match. It's packed with real physics and problem-solving, but written
with infectious optimism — the protagonist literally MacGyvers their way through
an extinction-level crisis, and it's a joy to follow along.

If you want something lighter and more absurdist, **The Hitchhiker's Guide to the
Galaxy** is a classic for a reason — it's essentially a comedy that happens to be
set in space, skewering physics, bureaucracy, and existence itself.

I'd save The Three-Body Problem for when you're in the mood for something more
epic and serious — it's excellent hard sci-fi but decidedly not optimistic.

────────────────────────────────────────────────────────────

You: quit
Goodbye!
```

---

## Project structure

```
rag-recommender/
├── build.gradle.kts                          # Kotlin + Ktor + serialization deps
├── settings.gradle.kts
├── README.md                                 # this file
└── src/main/kotlin/com/rag/
    ├── Main.kt                               # Entry point + interactive REPL
    ├── KnowledgeBase.kt                      # Catalogue of 16 movies & books
    │
    ├── model/
    │   └── Models.kt                         # MediaItem, EmbeddedChunk, SearchResult
    │
    ├── ollama/
    │   └── OllamaClient.kt                   # HTTP client for Ollama REST API
    │                                         #   POST /api/embeddings
    │                                         #   POST /api/chat
    ├── store/
    │   └── VectorStore.kt                    # In-memory vector DB
    │                                         #   index()  — embed + store
    │                                         #   search() — cosine similarity lookup
    └── rag/
        └── RagEngine.kt                      # RAG pipeline
                                              #   retrieve → augment → generate
```

### Component responsibilities

#### `OllamaClient`
Handles all HTTP communication with Ollama. Two methods:
- `embed(text)` — calls `/api/embeddings`, returns `List<Double>` (768-dim vector)
- `chat(messages)` — calls `/api/chat`, returns the assistant's reply string

#### `VectorStore`
A simple in-memory vector database backed by a `MutableList<EmbeddedChunk>`.
- `indexAll(items)` — embeds each item and stores it at startup
- `search(query, topK)` — embeds the query, computes cosine similarity against all stored vectors, returns top-K sorted by score

#### `RagEngine`
Orchestrates the three RAG steps:
1. **Retrieve** — calls `VectorStore.search()` for the top-4 candidates
2. **Augment** — formats candidates into a structured context block
3. **Generate** — constructs a system + user prompt and calls `OllamaClient.chat()`

#### `KnowledgeBase`
A hardcoded catalogue of `MediaItem` objects. Each item has a `toEmbeddableText()` method that serialises all its fields into a single string for embedding — genres, tags, description, year, rating. The richer this text, the better semantic search works.

---

## Configuration

All configuration is in `Main.kt`:

```kotlin
val ollama = OllamaClient(
    baseUrl    = "http://localhost:11434",  // change if Ollama runs elsewhere
    embedModel = "nomic-embed-text",        // swap for any embed model
    chatModel  = "phi3"                     // swap for llama3, mistral, etc.
)

val engine = RagEngine(store, ollama, topK = 4)  // increase topK for more candidates
```

---

## Extending the prototype

### Add more movies or books
Open `KnowledgeBase.kt` and add a new `MediaItem`. The richer the `description` and `tags`, the better the semantic matching.

```kotlin
MediaItem(
    id = "m9",
    title = "Annihilation",
    type = MediaType.MOVIE,
    genre = listOf("Sci-Fi", "Horror", "Mystery"),
    description = "A biologist signs up for a dangerous expedition into a mysterious zone where the laws of nature don't apply.",
    year = 2018,
    rating = 7.9,
    tags = listOf("Alex Garland", "surreal", "body-horror", "nature", "identity", "unsettling")
)
```

### Load items from a JSON file
Replace `KnowledgeBase.items` in `Main.kt`:

```kotlin
val items = Json.decodeFromString<List<MediaItem>>(
    File("knowledge_base.json").readText()
)
store.indexAll(items)
```

### Persist embeddings to disk
Avoid re-embedding on every startup by saving after indexing:

```kotlin
// Save
File("embeddings.json").writeText(Json.encodeToString(store.chunks))

// Load
val saved = Json.decodeFromString<List<EmbeddedChunk>>(File("embeddings.json").readText())
store.loadFromDisk(saved)  // add this method to VectorStore
```

### Swap in a production vector database
Replace `VectorStore` with a client for [Chroma](https://www.trychroma.com/),
[pgvector](https://github.com/pgvector/pgvector), or [Qdrant](https://qdrant.tech/).
The `RagEngine` only calls `store.search()` — so swapping the store requires
no changes to the RAG logic.

### Enable streaming responses
In `OllamaClient`, set `stream = true` in `ChatRequest` and consume the
newline-delimited JSON stream:

```kotlin
http.preparePost("$baseUrl/api/chat") { ... }.execute { response ->
    response.bodyAsChannel().consumeEach { line ->
        val chunk = json.decodeFromString<ChatResponse>(line)
        print(chunk.message.content)  // prints token by token
    }
}
```

### Add conversation memory
Pass the full message history to `ollama.chat()` to support follow-up questions like
"tell me more about the second one":

```kotlin
val history = mutableListOf<ChatMessage>()
// ... on each turn:
history.add(ChatMessage("user", userInput))
val reply = ollama.chat(systemPrompt + history)
history.add(ChatMessage("assistant", reply))
```

---

## How cosine similarity works

The vector store ranks items by **cosine similarity** — a measure of how closely
two vectors point in the same direction in high-dimensional space, regardless of
their magnitude.

```
similarity = (A · B) / (|A| × |B|)   →   range: -1 (opposite) to 1 (identical)
```

In practice, embedding vectors for semantically similar text cluster together.
"Mind-bending puzzle" ends up close to "complex layered plot" and "dream logic" —
even though none of those exact words appear in your query.

A score above ~0.80 is a strong match; below ~0.60 is likely a weak one.

---

## Dependencies

| Library | Version | Purpose |
|---|---|---|
| Kotlin | 1.9.22 | Language |
| Ktor Client (CIO) | 2.3.7 | Async HTTP for Ollama calls |
| kotlinx.serialization | 1.6.2 | JSON encode/decode |
| kotlinx.coroutines | 1.7.3 | Suspend functions |
| Logback | 1.4.14 | Logging (suppressed in REPL mode) |

---

## Troubleshooting

**`Connection refused` on startup**
Ollama isn't running. Start it with `ollama serve`.

**`model not found` error**
You haven't pulled the model yet. Run `ollama pull nomic-embed-text` and `ollama pull phi3`.

**Indexing is very slow**
First run of `nomic-embed-text` loads the model into memory (~5–10s). Subsequent embeddings are fast. If it stays slow, check `ollama ps` to see if the model loaded correctly.

**Recommendations seem off / generic**
Try adding more descriptive `tags` and a richer `description` to your `KnowledgeBase` items — the quality of embeddings depends heavily on the richness of the text being embedded.

---

## License

MIT — use freely, modify as needed.
