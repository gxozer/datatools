package com.rag.store

import com.rag.ollama.OllamaClient
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class VectorStoreTest {

    private val store = VectorStore(OllamaClient())

    @Test
    fun `identical vectors have similarity of 1`() {
        val a = listOf(1.0, 2.0, 3.0)
        assertEquals(1.0, store.cosineSimilarity(a, a), 1e-9)
    }

    @Test
    fun `orthogonal vectors have similarity of 0`() {
        val a = listOf(1.0, 0.0)
        val b = listOf(0.0, 1.0)
        assertEquals(0.0, store.cosineSimilarity(a, b), 1e-9)
    }

    @Test
    fun `zero vector yields similarity of 0 instead of dividing by zero`() {
        val zero = listOf(0.0, 0.0, 0.0)
        val other = listOf(1.0, 2.0, 3.0)
        assertEquals(0.0, store.cosineSimilarity(zero, other))
    }

    @Test
    fun `mismatched dimensions throw`() {
        assertFailsWith<IllegalArgumentException> {
            store.cosineSimilarity(listOf(1.0, 2.0), listOf(1.0))
        }
    }
}
