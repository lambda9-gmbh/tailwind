package de.lambda9.tailwind.core.extensions

import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

internal class MapExtensionsTest {

    @Test
    fun `test unionWith does not call merge if there is nothing to merge`() {
        val a = mapOf("a" to 5)
        val b = mapOf("b" to 6)
        val result = a.unionWith(b) { _, x, _ -> x }
        assertEquals(mapOf("a" to 5, "b" to 6), result)
    }

    @Test
    fun `test unionWith uses merge function to combine same keys`() {
        val a = mapOf("a" to 5)
        val b = mapOf("a" to 6)
        val result = a.unionWith(b) { _, _, y -> y }
        assertEquals(mapOf("a" to 6), result)
    }

    @Test
    fun `test unionWith works with null values`() {
        val a = mapOf("a" to null)
        val b = mapOf("a" to 6)
        val result = a.unionWith(b) { _, _, y -> y }
        assertEquals(mapOf("a" to 6), result)
    }

}