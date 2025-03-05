package de.lambda9.tailwind.core

import de.lambda9.tailwind.core.extensions.kio.andThen
import de.lambda9.tailwind.core.extensions.kio.foldCauseM
import de.lambda9.tailwind.core.extensions.kio.recover
import de.lambda9.tailwind.core.extensions.kio.run
import java.io.Serializable

/**
 * An [IO] is a [KIO], which may fail with an error of
 * type E or succeed with a value of type A.
 */
typealias IO<E, A> = KIO<Any?, E, A>

/**
 * A [Task] is an [IO], which may fail with a [Throwable]
 */
typealias Task<A> = IO<Throwable, A>

/**
 * [UIO] stands for unexceptional [IO], meaning an effectful computation,
 * which is assumed to never fail.
 *
 * An example of where this is the case is `LocalDateTime.now()`. It is a
 * side effect and should be handled as such, because we are reading the
 * clock time from
 */
typealias UIO<A> = IO<Nothing, A>

/**
 * [URIO] stands for unexceptional [IO], meaning an effectful computation,
 * which is assumed to never fail, but in an environment.
 *
 * An example of where this is the case is `LocalDateTime.now()`. It is a
 * side effect and should be handled as such, because we are reading the
 * clock time from
 */
typealias URIO<R, A> = KIO<R, Nothing, A>

/**
 * An [KIO] represents an effectful computation, which runs in an
 * environment of type [R] and produces either an error of type [E]
 * or a value of type [A].
 */
sealed class KIO<in R, out E, out A>: Serializable {

    /**
     * Returns a new [KIO], which re-runs this computation forever.
     */
    val forever: KIO<R, E, Nothing> get() =
        andThen { forever }

    /**
     * Returns a new [KIO], which applies the given function [f] to
     * a successful the computed value [A] to retrieve a value of [B].
     *
     * The contained function should *never* perform side effects.
     *
     * @param f a function to be applied
     * @return a new [KIO] mapping the function to a successful value
     */
    fun <B> map(f: (A) -> B): KIO<R, E, B> =
        FlatMap(this) { ok(f(it)) }

    /**
     * Returns a new [KIO], which applies the given function [h] to
     * the error [E] to retrieve a new error [E1].
     *
     * @param h map the given error to [E1]
     * @return a new [KIO] with a transformed error
     */
    fun <E1> mapError(h: (E) -> E1): KIO<R, E1, A> =
        recover { fail(h(it)) }

    /**
     * Returns a new [KIO], which
     *
     * @param env any environment
     */
    @Deprecated("Bitte ")
    internal fun provide(env: R): IO<E, A> =
        Provide(this, env)

    /**
     * The [ComprehensionScope] defines a scope for a [comprehension].
     *
     * In this scope, it is possible to retrieve the value of a [KIO]
     * by using either [invoke] or the `by` keyword of Kotlin.
     *
     * ## Example
     *
     * Here is an example.
     *
     * ```kotlin
     *
     * ```
     */
    interface ComprehensionScope<R, E> {

        /**
         * Run this [KIO] and return the resulting value.
         *
         *
         * ```kotlin
         * val program = KIO.comprehension {
         *     !launchMissiles()
         * }
         * ```
         */
        operator fun <A> KIO<R, E, A>.not(): A

    }

    internal data class Success<in R, out E, out A>(
        val value: A
    ): KIO<R, E, A>()

    internal data class Failure<in R, out E, out A>(
        val error: Cause<E>
    ): KIO<R, E, A>()

    internal data class Access<in R, out E, out A>(
        val f: (R) -> KIO<R, E, A>
    ): KIO<R, E, A>()

    internal data class FlatMap<R, R1: R, E: E1, E1, A, B>(
        val app: KIO<R, E, A>,
        val f: (A) -> KIO<R1, E1, B>
    ): KIO<R1, E1, B>()

    internal data class Fold<in R, E, out E2, A, out B>(
        val app: KIO<R, E, A>,
        val success: (A) -> KIO<R, E2, B>,
        val failure: (Cause<E>) -> KIO<R, E2, B>
    ): KIO<R, E2, B>(), (A) -> KIO<R, E2, B> {

        override fun invoke(p1: A): KIO<R, E2, B> =
            success(p1)

    }

    internal data class EffectPartial<in R, out A>(
        val effect: () -> A
    ): KIO<R, Throwable, A>()

    internal data class EffectTotal<in R, out E, out A>(
        val value: () -> A
    ): KIO<R, E, A>()

    internal data class Comprehension<in R, out E, A>(
        val f: ComprehensionScope<@UnsafeVariance R, @UnsafeVariance E>.() -> KIO<R, E, A>
    ): KIO<R, E, A>()

    internal data class Provide<in R, out E, A>(
        val kio: KIO<R, E, A>,
        val env: @UnsafeVariance R
    ): KIO<Any?, E, A>()

    companion object {

        /**
         * Returns a new [Task], which effect-fully runs the given value and
         * catches any [Throwable], that is not fatal.
         *
         * @param value a value
         * @return a new [Task], which runs the given value
         */
        operator fun <A> invoke(value: () -> A): Task<A> =
            effect(value)

        /**
         * Returns an [UIO], which always succeeds with the given value.
         *
         * @param value any value
         * @return a new [UIO]
         */
        fun <A> ok(value: A): UIO<A> =
            Success(value)

        /**
         * Returns a [KIO], which
         *
         * @return a new [KIO]
         */
        fun <E, A> done(exit: Exit<E, A>): IO<E, A> =
            exit.fold(::halt, ::ok)

        /**
         * Returns an [IO], which always fails with the given
         * error.
         *
         * @param error any value of type [E]
         * @return a new [IO]
         */
        fun <E> fail(error: E): IO<E, Nothing> =
            halt(Cause.expected(error))

        /**
         * Returns an [IO], which always fails with the
         * given [Cause].
         *
         * @param cause any cause of type [E]
         * @return a new [IO]
         */
        fun <E> halt(cause: Cause<E>): IO<E, Nothing> =
            Failure(cause)

        /**
         * Returns a new [KIO], which emulates a `for`-comprehension or
         * `do`-notation. This means, instead of nesting [andThen] calls,
         * to allow access to results of previous computations, we can
         * write them more linearly.
         *
         * @param f a function with a special receiver type, allowing to run
         *          [KIO] values from within.
         * @return a new [KIO]
         */
        fun <R, E, A> comprehension(f: ComprehensionScope<R, E>.() -> KIO<R, E, A>): KIO<R, E, A> =
            Comprehension(f)

        /**
         * Returns an effectual computation, which will try to run the
         * given function [f] and catch any [Throwable] errors.
         *
         * @param f a lazy function returning a value of type [A]
         * @return a new [KIO]
         */
        fun <A> effect(f: () -> A): Task<A> =
            EffectPartial(f)

        /**
         * Returns an effectful computation, which will not catch errors
         * thrown inside the function [f].
         *
         * This is mainly useful for functions such as `LocalDateTime.now()`,
         * which will not throw any exceptions.
         *
         * # Example
         *
         * ```kotlin
         * val getDate = KIO.effect { LocalDateTime.now() }
         * ```
         *
         * @param f a function running an effect.
         * @return a new [KIO]
         */
        fun <A> effectTotal(f: () -> A): UIO<A> =
            EffectTotal(f)

        /**
         * Returns a new [KIO], which allows access to the environment
         * of type [R].
         *
         * @return a new [KIO]
         */
        fun <R> access(): KIO<R, Nothing, R> =
            accessM { ok(it) }

        /**
         * Returns a new [KIO], which allows access to part of the environment
         * of type [R].
         *
         * @return a new [KIO]
         */
        fun <R, A> access(f: (R) -> A): KIO<R, Nothing, A> =
            accessM { ok(f(it)) }

        /**
         * Returns a [KIO], which allows to work with the environment
         * and directly run an action.
         *
         * @param f a function returning a new action using the environment
         * of type [R].
         * @return a new [KIO]
         */
        fun <R, E, A> accessM(f: (R) -> KIO<R, E, A>): KIO<R, E, A> =
            Access(f)

        /**
         * A computation, which always succeeds returning [Unit].
         */
        val unit: UIO<Unit> =
            ok(Unit)

        /**
         * Returns a KIO which fails, if the [predicate] resolves to true.
         *
         * This is very useful for checking invariants before performing an actions.
         *
         * ## Example
         *
         * ```kotlin
         * !KIO.failOn(!course.type.isG() && maybeSecondGCourse != null) {
         *     Error.OnlyTwoGCoursesAllowed(course, maybeSecondGCourse)
         * }
         * ```
         *
         * @param predicate
         * @param f
         */
        fun <E: E1, E1> failOn(predicate: Boolean, f: () -> E): KIO<Any?, E1, Unit> =
            if (predicate) fail(f()) else unit

        /**
         * Returns a KIO which fails, if the [predicate] resolves to true.
         *
         * This is very useful for checking invariants before performing an actions.
         *
         * ## Example
         *
         * ```kotlin
         * !KIO.failOn(!course.type.isG() && maybeSecondGCourse != null) {
         *     Error.OnlyTwoGCoursesAllowed(course, maybeSecondGCourse)
         * }
         * ```
         *
         * @param predicate
         * @param f
         */
        fun <R, E: E1, E1> failOnM(predicate: KIO<R, E, Boolean>, f: () -> E1): KIO<R, E1, Unit> =
            predicate andThen { failOn(it, f) }

        /**
         * Returns a [KIO] which fails with the given error, if the given [value] is null.
         *
         * This is very useful for checking invariants before performing an action.
         *
         * ## Example
         *
         * ```kotlin
         * val password = !KIO.failOnNull(userPassword) {
         *     Error.UnknownPassword
         * }
         * ```
         */
        fun <E, A> failOnNull(value: A?, error: () -> E): IO<E, A> =
            if (value == null) fail(error()) else ok(value)

        /**
         * Returns a new [KIO], which acquires a resource using [acquire],
         * releases it after usage using [release] and uses it using [use].
         *
         * If [release] fails, the whole computation will fail. If this is not
         * desired, the error can be logged and ignored in [release].
         *
         * The reason [release] cannot return any error, is that we would be
         * in an ill-defined situation, if [release] *and* [use] both failed.
         * Therefore, release should not fail and if it does (in the form of
         * throwing any [Throwable]), it will fail the whole action.
         *
         * **Note**: The main difference between this function and [bracketExit]
         * is, that [bracketExit] allows to use the result of [use], while this
         * is just releasing the resource.
         *
         * # Example
         *
         * ```kotlin
         * bracket(
         *     acquire = KIO.effect { FileOutputStream(File("Somewhere")) },
         *     release = { out -> KIO.effectTotal { out.close() } },
         *     use = { out -> KIO.effect { out.write("Test".toBytes()) }.refineOrDie(IOException::class) },
         * )
         * ```
         *
         * **Note**: If you want to use something like the above, which means
         * using an [AutoCloseable] resource, you can also just use the following
         *
         * ```kotlin
         * KIO.effect { FileOutputStream(File("Somehwere")) }.useM { out ->
         *     KIO.effect { out.write("Test".toBytes()) }
         * }
         * ```
         *
         * @param acquire acquire a resource using this function
         * @param release release a resource using this function
         * @param use use the resource of type [A] with this function
         * @return a new [KIO]
         */
        fun <R, E, A, B> bracket(
            acquire: KIO<R, E, A>,
            release: (A) -> URIO<R, Any?>,
            use: (A) -> KIO<R, E, B>
        ): KIO<R, E, B> =
            bracketExit(acquire, { r, _ -> release(r) }, use)

        /**
         * Returns a new [KIO], which acquires a resource using [acquire],
         * releases it after usage using [release] and uses it using [use].
         *
         * If [release] fails, the whole computation will fail. If this is not
         * desired, the error can be logged and ignored in [release].
         *
         * The reason [release] cannot return any error, is that we would be
         * in an ill-defined situation, if [release] *and* [use] both failed.
         * Therefore release should not fail and if it does (in the form of
         * throwing any [Throwable]), it will fail the whole action.
         *
         * # Example
         *
         * ```kotlin
         * bracketExit(
         *     acquire = KIO.effect { FileOutputStream(File("Somewhere")) },
         *     release = { out, exit -> out.close() },
         *     use = { out -> KIO.effect { out.write("Test".toBytes()) } },
         * )
         * ```
         *
         * @param acquire acquire a resource using this function
         * @param release release a resource using this function
         * @param use use the resource of type [A] with this function
         * @return a new [KIO]
         */
        internal fun <R, E, A, B> bracketExit(
            acquire: KIO<R, E, A>,
            release: (A, Exit<E, B>) -> URIO<R, Any?>,
            use: (A) -> KIO<R, E, B>
        ): KIO<R, E, B> = acquire.andThen { resource ->
            use(resource).run().andThen { exit ->
                release(resource, exit).foldCauseM(
                    onFailure = { cause2 ->
                        done(exit.fold(
                            onError = { a -> Exit.fail(a) },
                            onSuccess = { Exit.fail(cause2) }
                        ))
                    },
                    onSuccess = { done(exit) }
                )
            }
        }

        /**
         * Returns a new successful [KIO] for this value of type [A].
         *
         * ### Example
         *
         * This is especially useful for stuff
         *
         * ```kotlin
         * assertEquals(5.ok(), KIO.ok(5))
         * ```
         *
         * @return a new [KIO]
         */
        @JvmName("okExt")
        fun <A> A.ok(): UIO<A> =
            ok(this)

        /**
         * Returns a new [KIO], which failed with an error of type [E].
         *
         * @return a new [KIO]
         */
        @JvmName("failExt")
        fun <E> E.fail(): IO<E, Nothing> =
            fail(this)

        /**
         * Runs the given [KIO] with the given [env], by creating an
         * internal [Runtime] and using it to run the [KIO].
         *
         * *This should never be used inside the KIO.comprehension*
         *
         * @param env an environment, which is used to run this.
         * @return an [Exit], which encapsulate
         */
        @Throws(VirtualMachineError::class)
        fun <R, E, A> KIO<R, E, A>.unsafeRunSync(env: R): Exit<E, A> =
            Runtime.new(env).unsafeRunSync(this)

        /**
         * Runs the given [KIO] with the given [env], by creating an
         * internal [Runtime] and using it to run the [KIO].
         *
         * *This should never be used inside the KIO.comprehension*
         *
         * @return an [Exit], which encapsulate
         */
        @Throws(VirtualMachineError::class)
        fun <E, A> IO<E, A>.unsafeRunSync(): Exit<E, A> =
            Runtime.new(Unit).unsafeRunSync(this)

    }

}