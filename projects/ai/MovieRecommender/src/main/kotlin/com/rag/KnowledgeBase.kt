package com.rag

import com.rag.model.MediaItem
import com.rag.model.MediaType

/**
 * The hardcoded knowledge base for the RAG prototype.
 *
 * Contains 16 curated movies and books. In a real application, this would
 * be replaced by items loaded from a database, JSON file, or CSV — but
 * hardcoding it here keeps the prototype self-contained and easy to understand.
 *
 * ## How items are used
 * At startup, [Main] calls [com.rag.store.VectorStore.indexAll] with [items].
 * Each item's [MediaItem.toEmbeddableText] output is embedded into a 768-dim
 * vector and stored in the vector index. The richer the [MediaItem.description]
 * and [MediaItem.tags], the better semantic search will work.
 *
 * ## Adding new items
 * Simply append a new [MediaItem] to the list. Re-run the app and it will be
 * indexed automatically on next startup. No other changes needed.
 *
 * ## Item quality tips
 * - Write descriptions that capture *mood and themes*, not just plot summary.
 * - Use [MediaItem.tags] for adjectives a user might say: "slow-burn", "uplifting",
 *   "disturbing", "cozy", "page-turner" — these are the words users type.
 * - Include the director/author name in tags for creator-based queries.
 */
object KnowledgeBase {

    val items: List<MediaItem> = listOf(

        // ── Movies ────────────────────────────────────────────────────────

        MediaItem(
            id = "m1",
            title = "Inception",
            type = MediaType.MOVIE,
            genre = listOf("Sci-Fi", "Thriller"),
            description = "A thief who steals corporate secrets through dream-sharing technology is given the inverse task of planting an idea into the mind of a CEO.",
            year = 2010,
            rating = 8.8,
            tags = listOf("mind-bending", "heist", "dreams", "complex-plot", "Christopher Nolan")
        ),
        MediaItem(
            id = "m2",
            title = "Interstellar",
            type = MediaType.MOVIE,
            genre = listOf("Sci-Fi", "Drama"),
            description = "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival. Explores themes of love, time, and sacrifice.",
            year = 2014,
            rating = 8.6,
            tags = listOf("space", "time-travel", "emotional", "physics", "Christopher Nolan")
        ),
        MediaItem(
            id = "m3",
            title = "Parasite",
            type = MediaType.MOVIE,
            genre = listOf("Thriller", "Drama", "Dark Comedy"),
            description = "Greed and class discrimination threaten the symbiotic relationship between the wealthy Park family and the destitute Kim clan.",
            year = 2019,
            rating = 8.5,
            tags = listOf("class-divide", "social-commentary", "suspense", "Korean", "Palme d'Or")
        ),
        MediaItem(
            id = "m4",
            title = "Everything Everywhere All at Once",
            type = MediaType.MOVIE,
            genre = listOf("Sci-Fi", "Comedy", "Drama"),
            description = "A middle-aged Chinese-American laundromat owner must connect with parallel universe versions of herself to prevent a powerful being from destroying the multiverse.",
            year = 2022,
            rating = 8.0,
            tags = listOf("multiverse", "family", "absurd", "emotional", "action", "A24")
        ),
        MediaItem(
            id = "m5",
            title = "Princess Mononoke",
            type = MediaType.MOVIE,
            genre = listOf("Animation", "Fantasy", "Adventure"),
            description = "A young prince becomes embroiled in a struggle between forest gods and an industrialist town consuming the forest. A nuanced tale with no clear villains.",
            year = 1997,
            rating = 8.3,
            tags = listOf("Studio Ghibli", "nature", "war", "Miyazaki", "environmentalism", "epic")
        ),
        MediaItem(
            id = "m6",
            title = "The Shawshank Redemption",
            type = MediaType.MOVIE,
            genre = listOf("Drama"),
            description = "Two imprisoned men bond over a number of years, finding solace and eventual redemption through acts of common decency.",
            year = 1994,
            rating = 9.3,
            tags = listOf("hope", "friendship", "prison", "inspiring", "Stephen King adaptation")
        ),
        MediaItem(
            id = "m7",
            title = "Arrival",
            type = MediaType.MOVIE,
            genre = listOf("Sci-Fi", "Drama"),
            description = "A linguist is recruited to communicate with alien lifeforms after 12 mysterious spacecraft appear around the world. A story about language, time, and loss.",
            year = 2016,
            rating = 7.9,
            tags = listOf("aliens", "linguistics", "time", "emotional", "slow-burn", "thoughtful")
        ),
        MediaItem(
            id = "m8",
            title = "Oldboy",
            type = MediaType.MOVIE,
            genre = listOf("Thriller", "Mystery", "Drama"),
            description = "After being kidnapped and imprisoned for 15 years, a man is released and given 5 days to discover why. A shocking and unforgettable Korean neo-noir.",
            year = 2003,
            rating = 8.4,
            tags = listOf("Korean", "revenge", "dark", "mystery", "Park Chan-wook", "brutal")
        ),

        // ── Books ─────────────────────────────────────────────────────────

        MediaItem(
            id = "b1",
            title = "Project Hail Mary",
            type = MediaType.BOOK,
            genre = listOf("Sci-Fi", "Adventure"),
            description = "A lone astronaut wakes up with no memory on a spaceship millions of miles from Earth and must figure out how to save humanity from an extinction-level threat.",
            year = 2021,
            rating = 9.2,
            tags = listOf("Andy Weir", "space", "problem-solving", "funny", "aliens", "optimistic")
        ),
        MediaItem(
            id = "b2",
            title = "The Hitchhiker's Guide to the Galaxy",
            type = MediaType.BOOK,
            genre = listOf("Sci-Fi", "Comedy"),
            description = "Moments after Earth is demolished for a galactic highway, Arthur Dent is whisked into a series of absurd and hilarious cosmic adventures.",
            year = 1979,
            rating = 9.0,
            tags = listOf("Douglas Adams", "absurd", "funny", "space", "satire", "classic")
        ),
        MediaItem(
            id = "b3",
            title = "Dune",
            type = MediaType.BOOK,
            genre = listOf("Sci-Fi", "Epic Fantasy"),
            description = "Set in a distant future, a noble family becomes embroiled in a war for the most valuable substance in the universe on a harsh desert planet.",
            year = 1965,
            rating = 8.8,
            tags = listOf("Frank Herbert", "epic", "politics", "religion", "ecology", "world-building", "classic")
        ),
        MediaItem(
            id = "b4",
            title = "The Name of the Wind",
            type = MediaType.BOOK,
            genre = listOf("Fantasy"),
            description = "The story of Kvothe, a legendary wizard/bard, told in his own voice — covering his childhood in a traveling troupe, his years at a magical university, and his adventures.",
            year = 2007,
            rating = 9.0,
            tags = listOf("Patrick Rothfuss", "magic", "coming-of-age", "music", "lyrical prose", "tavern")
        ),
        MediaItem(
            id = "b5",
            title = "Flowers for Algernon",
            type = MediaType.BOOK,
            genre = listOf("Sci-Fi", "Drama"),
            description = "Told through diary entries, a man with an intellectual disability undergoes an experimental procedure to increase intelligence — with tragic consequences.",
            year = 1966,
            rating = 8.9,
            tags = listOf("Daniel Keyes", "emotional", "intelligence", "humanity", "heartbreaking", "classic")
        ),
        MediaItem(
            id = "b6",
            title = "A Gentleman in Moscow",
            type = MediaType.BOOK,
            genre = listOf("Historical Fiction", "Drama"),
            description = "A Russian count is sentenced to house arrest in a grand hotel for the rest of his life, from 1922 onward. A warm, witty, and deeply humane story.",
            year = 2016,
            rating = 8.8,
            tags = listOf("Amor Towles", "Russia", "charming", "elegant", "slow-burn", "optimistic")
        ),
        MediaItem(
            id = "b7",
            title = "The Three-Body Problem",
            type = MediaType.BOOK,
            genre = listOf("Sci-Fi", "Hard Sci-Fi"),
            description = "Set against China's Cultural Revolution and its aftermath, a secret military project makes contact with an alien civilization on the verge of chaos.",
            year = 2008,
            rating = 8.7,
            tags = listOf("Liu Cixin", "physics", "alien contact", "Chinese", "trilogy", "hard sci-fi", "epic")
        ),
        MediaItem(
            id = "b8",
            title = "Piranesi",
            type = MediaType.BOOK,
            genre = listOf("Fantasy", "Mystery"),
            description = "A man lives in a strange house with infinite halls, tidal statues, and only two other humans. He keeps meticulous journals as he tries to understand his world.",
            year = 2020,
            rating = 8.5,
            tags = listOf("Susanna Clarke", "surreal", "mystery", "short", "unique", "dreamlike", "labyrinth")
        )
    )
}
