package com.rag.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ModelsTest {

    private fun item(tags: List<String> = emptyList()) = MediaItem(
        id = "t1",
        title = "Test Title",
        type = MediaType.MOVIE,
        genre = listOf("Drama", "Comedy"),
        description = "A test description.",
        year = 2000,
        rating = 7.5,
        tags = tags
    )

    @Test
    fun `embeddable text includes tags when present`() {
        val text = item(tags = listOf("funny", "heartfelt")).toEmbeddableText()
        assertEquals(
            "Test Title (2000) — Movie | Genres: Drama, Comedy | Rating: 7.5/10 | Tags: funny, heartfelt | A test description.",
            text
        )
    }

    @Test
    fun `embeddable text omits tags segment when tags are empty`() {
        val text = item(tags = emptyList()).toEmbeddableText()
        assertFalse(text.contains("Tags:"), "should not contain a Tags: segment when tags are empty")
        assertEquals(
            "Test Title (2000) — Movie | Genres: Drama, Comedy | Rating: 7.5/10 | A test description.",
            text
        )
    }
}
