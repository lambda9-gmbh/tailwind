package de.lambda9.tailwind.core

import kotlin.contracts.contract


/**
 * An [Exit] is the result of a computation, which has either failed
 * with an error of type [E] or an unexpected error or succeeded with a
 * value of type [A].
 */
sealed class Exit<out E, out A> {

    data class Success<out A>(
        val value: A,
    ): Exit<Nothing, A>()

    data class Failure<out E>(
        val error: Cause<E>,
    ): Exit<E, Nothing>()

    /**
     * Returns a new [Exit], which applies the given function [f] to
     * a successful the computed value [A] to retrieve a value of [B].
     *
     * The contained function should *never* perform side effects.
     *
     * @param f a function to be applied
     * @return a new [Exit] mapping the function to a successful value
     */
    fun <B> map(f: (A) -> B): Exit<E, B> = fold(
        onSuccess = { Success(f(it)) },
        onError = { Failure(it) }
    )

    /**
     * Returns a new [Exit], which applies the given function [h] to
     * the error [E] to retrieve a new error [E1].
     *
     * @param f map the given error to [E1]
     * @return a new [Exit] with a transformed error
     */
    fun <E1> mapError(f: (E) -> E1): Exit<E1, A> = fold(
        onSuccess = { Success(it) },
        onError = { Failure(it.map(f)) }
    )

    /**
     *
     * @param onSuccess
     * @param onError
     */
    inline fun <B> fold(
        onError: (Cause<E>) -> B,
        onSuccess: (A) -> B,
    ): B = when (this) {
        is Success ->
            onSuccess(value)

        is Failure ->
            onError(error)
    }

    companion object {

        /**
         * Returns a new successful value of [Exit].
         *
         * @param value the contained value
         * @return a new [Exit]
         */
        fun <A> ok(value: A): Exit<Nothing, A> =
            Success(value)

        /**
         * Returns a new failing value of [Exit].
         *
         * @param value the cause value
         * @return a new [Exit]
         */
        fun <E> fail(error: Cause<E>): Exit<E, Nothing> =
            Failure(error)

        /**
         * Returns a new failing value of [Exit].
         *
         * @param value the expected error value
         * @return a new [Exit]
         */
        fun <E> fail(error: E): Exit<E, Nothing> =
            Failure(Cause.expected(error))

        /**
         * Returns a new failing value of [Exit].
         *
         * @param value the unexpected error value
         * @return a new [Exit]
         */
        fun panic(error: Throwable): Exit<Nothing, Nothing> =
            Failure(Cause.panic(error))

        /**
         * Check if the given Exit is a successful value.
         */
        fun <E, A> Exit<E, A>.isSuccess(): Boolean =
            fold(onSuccess = { true }, onError = { false })

    }

}