package de.lambda9.tailwind.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ResultComprehensionTest {

    sealed class Problem {
        data class Except(val exception: Exception): Problem()
        data class Other(val i: Int): Problem()
    }

    fun doStuff(): Result<Exception, Int> =
        Result.Err(RuntimeException("Test"))

    @Test
    fun test() {
        val result: Result<Problem, Int> = Result.comprehension {
            val value = !doStuff().mapError(Problem::Except)

            Result.Ok(value + 5)
        }

        assertTrue { result is Result.Err }
    }

    @Test
    fun testWorks() {
        val result: Result<Problem, Int> = Result.comprehension {
            val value = !Result.Ok<Problem, Int>(5)

            if (value < 5)
                Result.Err(Problem.Other(value))
            else
                Result.Ok(value + 5)
        }

        assertTrue { result is Result.Ok }
        assertEquals((result as Result.Ok).value, 10)
    }

}