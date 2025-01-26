package de.lambda9.tailwind.core


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
     *
     * @param f
     */
    fun <B> map(f: (A) -> B): Exit<E, B> = fold(
        onSuccess = { Success(f(it)) },
        onError = { Failure(it) }
    )

    /**
     *
     * @param f
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

        fun <A> ok(value: A): Exit<Nothing, A> =
            Success(value)

        fun <E> fail(error: Cause<E>): Exit<E, Nothing> =
            Failure(error)

        fun <E> fail(error: E): Exit<E, Nothing> =
            Failure(Cause.expected(error))

        fun panic(error: Throwable): Exit<Nothing, Nothing> =
            Failure(Cause.panic(error))


        fun <E, A> Exit<E, A>.isSuccess(): Boolean =
            fold(onSuccess = { true }, onError = { false })

    }

}