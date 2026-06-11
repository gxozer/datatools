package com.rag.model

/**
 * Represents a single movie or book in the knowledge base.
 *
 * Each [MediaItem] is embedded at startup and stored in the [com.rag.store.VectorStore].
 * At query time, the item whose embedding is closest to the query embedding is retrieved.
 *
 * @property id       Unique identifier (e.g. "m1", "b3").
 * @property title    Display title of the movie or book.
 * @property type     Whether this is a [MediaType.MOVIE] or [MediaType.BOOK].
 * @property genre    List of genre labels (e.g. ["Sci-Fi", "Thriller"]).
 * @property description One-paragraph synopsis used for semantic search.
 * @property year     Release or publication year.
 * @property rating   Score out of 10 (e.g. IMDb or Goodreads rating).
 * @property tags     Optional free-form tags that improve semantic matching
 *                    (mood, director, themes, style).
 */
data class MediaItem(
    val id: String,
    val title: String,
    val type: MediaType,
    val genre: List<String>,
    val description: String,
    val year: Int,
    val rating: Double,
    val tags: List<String> = emptyList()
) {
    /**
     * Produces the text that gets embedded into a vector.
     *
     * Combines all meaningful fields — title, type, genres, rating, tags, and
     * description — into a single string. The richer this text, the better the
     * semantic search will perform. For example, a query like "cozy feel-good
     * movie" will match against the tags and description, not just the title.
     *
     * Example output:
     * ```
     * Inception (2010) — Movie | Genres: Sci-Fi, Thriller | Rating: 8.8/10 |
     * Tags: mind-bending, heist, dreams | A thief who steals corporate secrets…
     * ```
     */
    fun toEmbeddableText(): String {
        val tagsPart = if (tags.isNotEmpty()) "Tags: ${tags.joinToString(", ")} | " else ""
        return "$title ($year) — ${type.label} | Genres: ${genre.joinToString(", ")} | " +
            "Rating: $rating/10 | $tagsPart$description"
    }
}

/**
 * Distinguishes between the two supported media types.
 *
 * @property label Human-readable string used in prompts and display output.
 */
enum class MediaType(val label: String) {
    MOVIE("Movie"),
    BOOK("Book")
}

/**
 * A [MediaItem] paired with its embedding vector, as stored in the [com.rag.store.VectorStore].
 *
 * @property item      The original media item.
 * @property embedding Dense float vector produced by the embedding model
 *                     (768 dimensions for nomic-embed-text).
 */
data class EmbeddedChunk(
    val item: MediaItem,
    val embedding: List<Double>
)

/**
 * A retrieval result returned by [com.rag.store.VectorStore.search].
 *
 * @property item  The matched media item.
 * @property score Cosine similarity score in the range [0, 1].
 *                 Scores above ~0.80 indicate a strong semantic match;
 *                 below ~0.60 is usually a weak or incidental match.
 */
data class SearchResult(
    val item: MediaItem,
    val score: Double
)
