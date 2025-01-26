package de.lambda9.tailwind.core

import de.lambda9.tailwind.core.extensions.chunkedOnThreshold
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals


class ListTest {

    @Test
    fun test() {
        val input = listOf(1, 2, 3, 4, 5, 6)
        assertEquals(listOf(listOf(1), listOf(2, 3), listOf(4, 5), listOf(6)), input.chunkedOnThreshold { it % 2 == 0 })
    }
}