@file:Suppress("unused")
package de.lambda9.tailwind.core

import java.io.PrintWriter
import java.io.StringWriter


/**
 * A [Cause] combines an [Exception]nal cause and an expected cause of
 * a computation.
 *
 * When a [KIO] is run, there is always a risk of a [Throwable] being
 * thrown, which is currently not handled in most cases. To provide
 * a stable and generic error handling model in the case of Wicket for
 * example, it is therefore important to make sure every case is handled.
 */
sealed class Cause<out E> {

    /**
     * Returns a list of all unexpected errors.
     *
     * @return a list of all errors
     */
    fun defects(): List<Throwable> = fold(
        onExpected = { emptyList() },
        onPanic = { listOf(it) },
    )

    /**
     * Returns a list of all expected errors, so
     * all failures.
     *
     * @return a list of all failures
     */
    fun failures(): List<E> = fold(
        onExpected = { listOf(it) },
        onPanic = { emptyList() },
    )

    /**
     * Map the contained error into another error.
     *
     * @param f a function
     */
    fun <E2> map(f: (E) -> E2): Cause<E2> = fold(
        onExpected = { expected(f(it)) },
        onPanic = { panic(it) },
    )

    /**
     * Returns the first defect, if it exists.
     *
     * @return a Throwable, if it exists.
     */
    fun firstDefectOrNull(): Throwable? = fold(
        onExpected = { null },
        onPanic = { it },
    )

    /**
     * Returns the first Failure, if it exists.
     *
     * @return a value of type E
     */
    fun firstFailureOrNull(): E? = fold(
        onExpected = { it },
        onPanic = { null },
    )

    /**
     * Fold this [Cause]
     *
     * @param onExpected handles the expected error
     * @param onPanic handles the unexpected error
     */
    @Suppress("DEPRECATION")
    inline fun <B> fold(
        onExpected: (E) -> B,
        onPanic: (Throwable) -> B,
    ): B = when (this) {
        is Panic ->
            onPanic(cause)

        is Expected ->
            onExpected(cause)
    }

    @Deprecated("Please use Cause.panic, Cause.expected or Cause.fold for working with Causes")
    data class Panic(
        val cause: Throwable
    ): Cause<Nothing>()

    @Deprecated("Please use Cause.panic, Cause.expected or Cause.fold for working with Causes")
    data class Expected<out E>(
        val cause: E
    ): Cause<E>()

    fun prettyPrint(): String =
        fold(
            onExpected = {
                it?.toString() ?: "null"
            },
            onPanic = {
                val writer = StringWriter()
                it.printStackTrace(PrintWriter(writer))
                writer.toString()
            },
        )

    companion object {

        fun panic(t: Throwable): Cause<Nothing> =
            @Suppress("DEPRECATION")
            Panic(t)

        fun <E> expected(t: E): Cause<E> =
            @Suppress("DEPRECATION")
            Expected(t)

    }

}


