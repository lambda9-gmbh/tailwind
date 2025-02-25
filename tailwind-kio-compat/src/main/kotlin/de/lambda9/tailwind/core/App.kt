package de.lambda9.tailwind.core

import de.lambda9.tailwind.core.experimental.DangerouslyInternalAppException
import de.lambda9.tailwind.core.extensions.kio.catchError
import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.collect
import de.lambda9.tailwind.core.extensions.kio.recover

/**
 * An [App] represents a computation which runs in an environment [Env]
 * and may fail with an [Error].
 *
 * This is still here for backwards compatibility with our internal
 * applications and therefore not deprecated, but it should be considered.
 *
 * @author Matthias Metzger
 */
@JvmInline
value class App<Env, Error, T>(internal val kio: KIO<Env, Error, T>) {

    /**
     * Apply a function to an [App].
     *
     * @param f function to be applied.
     * @return if the [App] contains an [Error], the [Error] will
     *  be propagated. If it succeeded, the function [f] will be applied.
     */
    infix fun <R> map(f: (T) -> R): App<Env, Error, R> = App(kio.map(f))

    /**
     * Chain together a sequence of [App] actions, which may fail.
     *
     * @param f function to be applied
     * @return If the [App] contains an error, the error will be propagated.
     * If it succeeded, the function will be applied.
     */
    infix fun <R> andThen(f: AndThenScope<Env, Error>.(T) -> App<Env, Error, R>): App<Env, Error, R> =
        App(kio.andThen { value ->
            KIO.accessM<Env, Error, R> { env ->
                try {
                    f(AndThenScope(env), value).kio
                } catch (ex: DangerouslyInternalAppException) {
                    @Suppress("UNCHECKED_CAST")
                    KIO.fail(ex.error as Error)
                }
            }
        })

    /**
     * Chain together a sequence of [App] actions, which may fail and
     * discard the argument.
     *
     * @param other other application to execute after this
     * @return If the [App] contains an error, the error will be propagated.
     * If it succeeded, the function will be applied.
     */
    infix fun <R> andThen(other: App<Env, Error, R>): App<Env, Error, R> = andThen {
        other
    }

    /**
     * Chain together a sequence of [App] actions, which may fail and
     * perform the given action as a side effect.
     *
     * @param f function to be applied
     * @return If the [App] contains an error, the error will be propagated.
     * If it succeeded, the function will be applied.
     */
    @Deprecated("This method has been deprecated in favor of comprehensions", ReplaceWith("App.comprehension"))
    infix fun andThenDo(f: AndThenScope<Env, Error>.(T) -> App<Env, Error, Unit>): App<Env, Error, T> = andThen {
        a -> f(a).map { a }
    }

    /**
     * Apply a function to the error of an [App].
     *
     * @param h function to be applied
     * @return If the [App] contains an error, the function will
     * be applied. If it contains a value, the value will be propagated.
     */
    infix fun <E> mapError(h: (Error) -> E): App<Env, E, T> =
        App(kio.mapError(h))

    /**
     * Handle the error of a failed computation.
     *
     * This is useful anytime an error can be mitigated. It can
     * also be used as an `or`.
     *
     * Example:
     *
     * ```kotlin
     * typealias TodoApp<T> = App<Unit, Error, T>
     *
     * val a = throwError(CannotRemove())
     * val b = of(5)
     *
     * println(a.catchError(b).run(Unit)) // <- 5
     * ```
     *
     * @param h function to be applied
     * @return If the [App] contains an error, the function will
     * be applied. If it contains a value, the value will be propagated.
     */
    infix fun catchError(h: (Error) -> App<Env, Error, T>): App<Env, Error, T> =
        App(kio.recover { h(it).kio })

    /**
     * Provider another result, in case the computation failed.
     *
     * Examples:
     *
     * ```kotlin
     * of(5)             orElse of(6)             == of(5)
     * throwError("err") orElse of(5)             == of(5)
     * throwError("")    orElse throwError("err") == throwError("err")
     * ```
     *
     * @param other another computation
     * @return If the result of this [App] is an [Result.Ok], the
     * value of the computation will be propagated. Otherwise,
     * the result of [other] will be used taken as other value.
     */
    infix fun orElse(other: App<Env, Error, T>): App<Env, Error, T> =
        catchError { other }


    /**
     * Run an [App] within an [Env].
     *
     * @param env environment to be used
     * @return a [Result] representing a failure or success
     * of the computation.
     */
    infix fun run(env: Env): Result<Error, T> {
        val runtime = Runtime.new(env)
        return when (val exit = runtime.unsafeRunSync(kio)) {
            is Exit.Success ->
                Result.Ok<Error, T>(exit.value)

            is Exit.Failure -> exit.error.fold(
                onExpected = { Result.fail(it) },
                onPanic = { throw it },
            )
        }
    }

    companion object {

        /**
         * Returns an [App], which always succeeds with
         * the given [value].
         *
         * Here is an example of how to use it.
         *
         * ```kotlin
         * typealias TodoApp = App<Unit, Unit, Int>
         *
         * val x = App.of<(5)
         * ```
         *
         * @param value value to be returned
         * @return a new [App]
         */
        fun <Env, E, T> of(value: T): App<Env, E, T> =
            App(KIO.ok(value))

        /**
         * Convert a [Result] into an [App].
         *
         * @return if the [Result] contains an error, the [App] will contain an
         * error as well. If the [Result] contains a value, the [App] will
         * contain the value as well.
         */
        fun <Env, Error, T> of(value: Result<Error, T>): App<Env, Error, T> =
            App(value.fold({ KIO.fail(it) }, {KIO.ok(it)}))

        /**
         * Returns an [App], which always succeeds with its
         * environment [Env].
         *
         * This is useful, if the whole environment is needed for
         * a computation, i.e. if the environment is a database
         * connection.
         *
         * Example:
         *
         * ```kotlin
         * typealias TodoApp<T> = App<DSLContext, TaskError, T>
         *
         * fun load(id: Long): TodoApp<TodoData> = ask().andThen { dsl ->
         *   dsl. // <- do stuff
         * }
         *
         * fun query<Env, Error>(f: (Env) -> App<Env, Error, T>): App<Env, Error, T> =
         *   ask().andThen(f)
         * ```
         *
         * @return a new [App],
         */
        fun <Env, Error> ask(): App<Env, Error, Env> =
            App(KIO.access())

        /**
         * Returns an [App], which always succeeds by applying [f]
         * to the environment.
         *
         * This is useful, if only part of the environment is needed
         * for a computation. Imagine a composite environment, which
         * contains a database connection and only the database connection
         * is needed for a computation.
         *
         * Example:
         *
         * ```kotlin
         * data class TodoEnv(dsl: DSLContext, userName: String)
         * typealias TodoApp<T> = App<TodoEnv, TodoError, T>
         *
         * fun load(id: Long): TodoApp<Task> = asks(TodoEnv::dsl).andThen { dsl ->
         *   dsl. // <- do stuff
         * }
         * ```
         *
         * @param f function to be applied
         * @return a new [App], applying the function to the
         * environment.
         */
        fun <Env, Error, T> asks(f: (Env) -> T): App<Env, Error, T> =
            ask<Env, Error>().map(f)

        /**
         * Returns an [App], which always fails with the
         * given [error].
         *
         * @param error error to be returned
         * @return a new, failing [App]
         */
        fun <Env, Error, T> throwError(error: ToError<Error>): App<Env, Error, T> =
            App(KIO.fail(error.toError()))

        /**
         * Conditional execution of the given [App].
         *
         * This is very useful for checking error conditions. Imagine
         * a given value, which is required, alas should not be `null`.
         *
         * ```kotlin
         * fun <T> require(value: T): App<Env, Error, Unit> =
         *   when_(value == null, throwError(Required()))
         * ```
         *
         * @param condition a condition
         * @param then the action to be executed
         * @return if the given [condition] is true, [then] will be
         * executed. Otherwise nothing will happen.
         */
        fun <Env, Error> `when`(condition: Boolean, then: App<Env, Error, Unit>): App<Env, Error, Unit> =
            if (condition) then else of(Unit)

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
        fun <Env, Error> unless(condition: Boolean, then: App<Env, Error, Unit>): App<Env, Error, Unit> =
            if (condition) of(Unit) else then

        /**
         * A comprehension for composing monadic actions.
         *
         * @param f
         * @return
         */
        fun <Env, E, E1: E, A, A1: A> comprehension(f: AndThenScope<Env, E>.() -> App<Env, E1, A1>): App<Env, E, A> =
            ask<Env, E>().andThen { env ->
                try {
                    @Suppress("UNCHECKED_CAST")
                    AndThenScope<Env, E>(env).f() as App<Env, E, A>
                } catch (ex: DangerouslyInternalAppException) {
                    @Suppress("UNCHECKED_CAST")
                    throwError<A>(ex.error as E)
                }
            }

        fun <R, E, A> of(kio: KIO<R, E, A>): App<R, E, A> =
            App(kio)

    }

}

/**
 * Sequences [App] computations from left to right. It fails
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
 * of values will be contained in the resulting [App].
 */
fun <Env, Error, T> Iterable<App<Env, Error, T>>.sequence(): App<Env, Error, List<T>> =
    App(map { it.kio }.collect())


/**
 * Traverse the given [Iterable], converting every element to a computation and sequence
 * the result. To better understand, the following law holds.
 *
 * ```kotlin
 * .traverse(f) == .map(f).sequence()
 * ```
 *
 * @param f a function to convert a value into an app
 * @return a computation, which when run yields
 */
fun <Env, Error, T, R> Iterable<T>.traverse(f: (T) -> App<Env, Error, R>): App<Env, Error, List<R>> =
    map(f).sequence()


/**
 * Returns a new [KIO] based on the given [de.lambda9.tailwind.core.App].
 *
 * @param app any effectful computation enapsulated in [de.lambda9.tailwind.core.App]
 * @return a new [KIO]
 */
fun <R, E, A> KIO.Companion.fromApp(app: App<R, E, A>): KIO<R, E, A> =
    app.kio

