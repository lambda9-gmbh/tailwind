package de.lambda9.tailwind.core

import java.io.Serializable


/**
 * A [Result] is the result of a computation, that may fail.
 *
 * This data type should be used, when known errors might occur, which
 * should be handled by the caller.
 *
 * @author Matthias Metzger
 */
sealed class Result<out E, out T>: Serializable {

    /**
     * The result of a successful computation.
     *
     * @param value the value of the computation
     */
    data class Ok<E, T>(val value: T) : Result<E, T>()

    /**
     * The result of a failed computation.
     *
     * @param error an error of the computation.
     */
    data class Err<E, T>(val error: E) : Result<E, T>()

    /**
     * Apply a function to a result.
     *
     * @param f a function to be applied to the result
     * @return If the result is [Ok], it will be
     * converted. If the result is an [Err], the
     * same error value will propagate through.
     */
    infix fun <R> map(f: (T) -> R): Result<E, R> =
        fold({ Err(it) }) { Ok(f(it)) }

    /**
     * Chain together a sequence of computations that may fail.
     *
     * @param f a function yielding a new result
     * @return if the result is [Ok], it will be converted
     * to a new [Result].
     */
    infix fun <R> andThen(f: (T) -> Result<@UnsafeVariance E, R>): Result<E, R> =
        fold(::Err, f)

    /**
     * Apply a function to the error of the result.
     *
     * @param f a function to be applied to the error
     * @return If the result is [Ok], the same value
     * will be propagated through. If the result is an
     * [Err], it will be converted.
     */
    infix fun <F> mapError(f: (E) -> F): Result<F, T> =
        fold({ Err(f(it)) }) { Ok(it) }

    /**
     * Handle the error of a failed computation.
     *
     * @param h function to be applied
     * @return If the [Result] is [Ok], the value will be
     * returned, otherwise the error will be converted to a
     * [T].
     */
    infix fun handleError(h: (E) -> @UnsafeVariance T): T =
        fold(h) { it }

    /**
     * Ignore the error of a failed computation.
     *
     * @return If the result is [Ok], the same value
     * will be returned. If the result is an [Err], it
     * will be `null`.
     */
    fun ignoreError(): T? =
        fold({ null }) { it }

    fun ignoreValue(): E? =
        fold({ it }, { null })

    /**
     * Handle the error of a failed computation.
     *
     * @param h function to be applied
     * @return If the result is [Ok], the value will be
     * propagated. If it is an [Err], the function will
     * be applied.
     */
    infix fun catchError(h: (E) -> Result<@UnsafeVariance E, @UnsafeVariance T>): Result<E, T> =
        fold(h, ::Ok)

    /**
     * Provide a default value for a failed computation.
     *
     * @param other a default value in case of an error
     * @return If the result is [Ok] the same value
     * will be returned. If the result is an [Err], the
     * default value will be returned.
     */
    infix fun withDefault(other: @UnsafeVariance T): T =
        fold({ other }) { it }

    /**
     * Provide another result for a failed computation.
     *
     * Examples:
     *
     * ```kotlin
     * val error = Err("err")
     *
     * Ok(5) or error == Ok(5)
     * error or Ok(5) == Ok(5)
     * error or error == error
     * ```
     *
     * @param other another result in case of an error
     * @return If the result is [Ok] the same result will
     * be returned. If the result is an [Err], the [other]
     * result will be used.
     */
    infix fun orElse(other: Result<@UnsafeVariance E, @UnsafeVariance T>): Result<E, T> =
        catchError { other }

    /**
     * Check if the given value is contained in the result.
     *
     * Due to operator overloading, this function can be used
     * with the `in` operator. It is very useful, while testing.
     *
     * Example:
     *
     * ```kotlin
     * 5 in Ok(5)     == true
     * 5 in Err(Unit) == false
     * 5 in Ok(6)     == false
     * ```
     *
     * @param value value to be checked
     * @return If the result is [Ok], the contained value will
     * be checked against the given [value]. If the result is
     * [Err], false will be returned.
     */
    operator fun contains(value: @UnsafeVariance T): Boolean =
        fold({ false }) { value == it }

    /**
     * Fold the [Result] into a single value. All of the other helpers
     * are implemented in terms of fold.
     *
     * @param onError an error handler
     * @param onSuccess a handler for a value
     * @return a new value of type [A]
     */
    inline fun <A> fold(onError: (E) -> A, onSuccess: (T) -> A): A = when (this) {
        is Ok ->
            onSuccess(value)

        is Err ->
            onError(error)
    }

    // This exception *will* and *must* only be used in a ComprehensionScope!
    private class InternalComprehensionException(val error: Any?): Exception()

    /**
     * A scope allowing to compute values better.
     */
    class ComprehensionScope<E> {

        /**
         * Retrieve the actual value [T]. If the computation fails,
         * no further computations inside this scope will be done.
         *
         * @return a value of type [T]
         */
        operator fun <T, E1: E> Result<E1, T>.not(): T = when (this) {
            is Ok ->
                value

            is Err ->
                throw InternalComprehensionException(error as Any?)
        }

    }

    companion object {

        /**
         * Returns a [Result], representing a success with the given value.
         *
         * @param value any value
         * @return a new [Result]
         */
        fun <A> ok(value: A): Result<Nothing, A> =
            Ok(value)

        /**
         * Returns a [Result], representing a failure with the given value.
         *
         * @param error any error
         * @return a new [Result]
         */
        fun <E> fail(error: E): Result<E, Nothing> =
            Err(error)

        /**
         * Catch any [Throwable] thrown in the given block.
         *
         * This is a very useful function for executing a potentially
         * erroneous computation. The [Throwable] may be converted
         * with [mapError] or [catchError] subsequently.
         *
         * It is even possible to use `when` for a multicatch.
         *
         * ```kotlin
         * Result.attempt {
         *   Files.readAllLines(Paths.get("abc"))
         * } mapError { t ->
         *   when (t) {
         *     is FileNotFoundException ->
         *       println("Wooops, file not found")
         *
         *     else ->
         *       throw it
         *   }
         * }
         * ```
         *
         * @param block an unsafe block to be
         * @return
         */
        @Deprecated(message = "This has never been used by anyone, so no.")
        fun <T> attempt(block: () -> T): Result<Throwable, T> =
            try {
                val result = block()
                Ok(result)
            } catch (e: Throwable) {
                Err(e)
            }


        /**
         * A comprehension, which allows to chain together monadic actions in a
         * linear way.
         *
         * If any result is an error, this function will exit immediately with
         * said error.
         *
         *
         * ## Example
         *
         * ```kotlin
         * sealed class Problem {
         *   data class Json(val ex: JsonProcessingException): Problem()
         * }
         *
         * val result: Result<Problem, Int> = Result.comprehension {
         *     val data = !JSON.parse("5", Int::class).mapError(Problem::Json)
         *     val other = !JSON.parse("6", Int::class).mapError(Problem::Json)
         *     Result.Ok(data + other)
         * }
         * ```
         *
         * @param f a function computing the next result
         * @return a new Result
         */
        fun <E, T> comprehension(f: ComprehensionScope<E>.() -> Result<E, T>): Result<E, T> =
            try {
                f(ComprehensionScope())
            } catch (ex: InternalComprehensionException) {
                @Suppress("UNCHECKED_CAST")
                Err(ex.error as E)
            }

    }
}


/**
 * Retrieve the value of this [Result] if the error
 * has been handled otherwise.
 */
val <T> Result<Nothing, T>.value: T
    get() = when (this) {
        is Result.Ok ->
            value

        is Result.Err ->
            throw IllegalStateException("""
                |It is not possible to create a Result.Err(Nothing),
                |because Nothing is uninhabitable, which means it cannot
                |be created, ever. Therefore a Result<Nothing, T> should
                |always be a Result.Ok, safe for casting.
            """.trimMargin())
    }


/**
 * Sequences [de.lambda9.tailwind.core.App] computations from left to right. It fails
 * with the first failure encountered.
 *
 * This is useful, when a number of computations need to be run
 * in sequence, which all yield the same type of result.
 *
 * Example:
 *
 * ```kotlin
 * typealias TodoApp<T> = App<DSLContext, TodoError, T>
 *
 * listOf(of(5), of(6)).sequence().andThen { xs -> xs.sum() }
 * ```
 *
 * @return If any of the provided computations contain an error,
 * the error will be propagated. If all of them succeed, the list
 * of values will be contained in the resulting [de.lambda9.tailwind.core.App].
 */
fun <Error, T> Iterable<Result<Error, T>>.sequence(): Result<Error, List<T>> {
    val oks = mutableListOf<T>()
    forEach {
        when (it) {
            is Result.Ok ->
                oks.add(it.value)

            is Result.Err ->
                return Result.Err(it.error)
        }
    }

    return Result.Ok(oks)
}

/**
 * Represent the result of a [partition] operation.
 *
 * @param values the results, which have been [Result.Ok]
 * @param errors the results, which have been [Result.Err]
 */
data class PartitionResult<out E, out T>(
    val values: List<T>,
    val errors: List<E>
)


/**
 * Collect all results into a [PartitionResult].
 *
 * @return a [PartitionResult] for this iterable
 */
fun <E, T> Iterable<Result<E, T>>.partition(): PartitionResult<E, T> {
    val oks = mutableListOf<T>()
    val errs = mutableListOf<E>()
    forEach {
        when (it) {
            is Result.Ok ->
                oks.add(it.value)

            is Result.Err ->
                errs.add(it.error)
        }
    }

    return PartitionResult(oks, errs)
}
