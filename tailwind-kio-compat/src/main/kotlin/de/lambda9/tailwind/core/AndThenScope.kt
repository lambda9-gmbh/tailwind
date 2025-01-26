package de.lambda9.tailwind.core

import de.lambda9.tailwind.core.experimental.DangerouslyInternalAppException

@DslMarker
annotation class AndThen

/**
 * The [AndThenScope] provides context-sensitive functions for creating
 * monadic actions inside any related [andThen] function.
 *
 * Its main intention is to help the compiler with type inference in
 * nested monadic actions.
 *
 * @param context any environment.
 */
open class AndThenScope<Env, E>(
    val context: Env
) {

    /**
     * Constructor for a fixed unit value.
     */
    val unit: App<Env, E, Unit> =
        of(Unit)

    /**
     * Returns an [App] which always succeeds with the
     * given [value].
     *
     * @param value any value
     * @return a new, always succeeding [App]
     */
    fun <T> of(value: T): App<Env, E, T> =
        App.of(value)


    /**
     * Returns an [App] which succeeds in the case of
     * a [Result.Ok] and fails in the case of a [Result.Err].
     *
     * @param result any result
     * @return a new App
     */
    fun <T, E1: E> of(result: Result<E1, T>): App<Env, E, T> =
        @Suppress("UNCHECKED_CAST")
        (App.of(result as Result<E, T>))


    /**
     * Returns an [App] which succeeds in the case of
     * a [Result.Ok] and fails in the case of a [Result.Err].
     *
     * @return a new App
     */
    fun <T, E1: E> Result<E1, T>.toApp(): App<Env, E, T> =
        of(this)


    /**
     * Returns an [App] which fails with the given error.
     *
     * @param error
     */
    @Suppress("DEPRECATION")
    fun <T> throwError(error: E): App<Env, E, T> =
        App.throwError(object : ToError<E> {
            override fun toError(): E = error
        })


    /**
     * Returns the value inside an [AndThenScope]. This function replaces
     * all usages of [fetch] and [fetchValue].
     *
     * ## Example
     *
     * Here is an example.
     *
     * ```kotlin
     * fun getName(customer: Customer.Pk) = jooqQuery<Env, Error, String> {
     *   val customer = !getCustomer(customer)
     *   of("${customer.firstName} ${customer.lastName}")
     * }
     * ```
     *
     * @return the actual value T, after running the
     * computation. If an error occurs, it will jump outside
     * the context.
     */
    operator fun <T, T1: T, E1: E> App<Env, E1, T1>.not(): T =
        when (val result = run(context)) {
            is Result.Ok -> result.value
            is Result.Err -> throw DangerouslyInternalAppException(result.error as Any)
        }

    operator fun <T, T1: T, E1: E> KIO<Env, E1, T1>.not(): T =
        App(this).not()

    /**
     * Retrieve the actual value, when you are in the Scope of an [AndThenScope].
     *
     * @return
     */
    @Deprecated("This method is outdated.", ReplaceWith("!myApp"))
    fun <T> App<Env, Nothing, T>.fetchValue(): T =
        run(context).value


    /**
     * Returns the value inside a [AndThenScope].
     *
     * *Word of caution*: This method is part of an experimental API
     * for composing monadic actions. Please try this out, but use
     * with caution and be careful, whether anything misbehaves.
     *
     * # Usage Guidelines
     *
     * This method should **never** be used in any function, which
     * expects a pure function, for example `.map`. This will cause
     * **unexpected** and **undefined behaviour** and may lead to
     * runtime errors. Use `.andThen` or other compositional functions
     * instead.
     *
     * You *can* use it instead of chaining and nesting `.andThen` calls.
     * Here is an example.
     *
     * ```kotlin
     * fun getName(pk: Customer.Pk) = jooqQuery<Env, Error, String> {
     *   getCustomer(pk).andThen {
     *     of("${customer.firstName} ${customer.lastName}")
     *   }
     * }
     *
     * // With this method
     * fun getName(customer: Customer.Pk) = jooqQuery<Env, Error, String> {
     *   val customer = getCustomer(customer).fetch()
     *   of("${customer.firstName} ${customer.lastName}")
     * }
     * ``
     *
     * @return the actual value
     */
    @Deprecated("This method is outdated.", ReplaceWith("!myApp"))
    fun <T> App<Env, E, T>.fetch(): T =
        when (val result = run(context)) {
            is Result.Ok ->
                result.value

            is Result.Err ->
                throw DangerouslyInternalAppException(result.error as Any)
        }



    /**
     * Conditional execution of the given [App], the reverse
     * of [when].
     *
     * This is very useful for checking error conditions. Imagine
     * a given integer, which needs to be bigger than 10.
     *
     * ```kotlin
     * fun biggerThan10(value: Int): App<Env, Error, Unit> =
     *   unless(value > 10, throwError(TooSmall()))
     * ```
     *
     * @param condition a condition
     * @param then an action to be executed
     * @return if the given [condition] is true, nothing will happen.
     * Otherwise the given [then] will be executed.
     */
    fun unless(condition: Boolean, then: App<Env, E, Unit>): App<Env, E, Unit> = when (condition) {
        true ->
            App.of(Unit)

        false ->
            then
    }


    /**
     * Conditional execution of the given [App], the reverse
     * of [when].
     *
     * This is very useful for checking error conditions. Imagine
     * a given integer, which needs to be bigger than 10.
     *
     * ```kotlin
     * fun biggerThan10(value: Int): App<Env, Error, Unit> =
     *   unless(value > 10, throwError(TooSmall()))
     * ```
     *
     * @param condition a condition
     * @param then an action to be executed
     * @return if the given [condition] is true, nothing will happen.
     * Otherwise the given [then] will be executed.
     */
    fun `when`(condition: Boolean, then: App<Env, E, Unit>): App<Env, E, Unit> = when (condition) {
        true ->
            then

        false ->
            App.of(Unit)
    }


    /**
     * Execute the given [action], if the value is null, otherwise return it.
     *
     * This method may be used in place of an [orElseOnNull], or to require
     * non-nullability of things, retrieved from the database.
     *
     * ```
     * jooqQuery {
     *   val result = dsl.selectFrom(..).where(..)
     *   whenNull(result, throwError(Required()))
     * }
     *
     * .andThen<Int> {
     *   whenNull(it, of(5))
     * }
     * ```
     *
     * @param value any nullable value
     * @param action any action, which returns a T, it may also throw an error
     * @return a new App
     */
    fun <T> whenNull(value: T?, action: App<Env, E, T>): App<Env, E, T> = when (value) {
        null ->
            action

        else ->
            of(value)
    }

}
