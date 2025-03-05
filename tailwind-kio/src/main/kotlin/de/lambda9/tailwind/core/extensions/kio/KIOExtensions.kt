@file:Suppress("unused")
package de.lambda9.tailwind.core.extensions.kio

import de.lambda9.tailwind.core.*
import java.time.Duration
import kotlin.reflect.KClass


/**
 * Returns a [KIO], which runs this [KIO], followed by passing its
 * value into the function [f] and returning the resulting [KIO].
 *
 * @param f a function, which will be applied to a successful value of type [A]
 * @return a new [KIO]
 */
infix fun <R, R1: R, E: E1, E1, A, B> KIO<R, E, A>.andThen(f: (A) -> KIO<R1, E1, B>): KIO<R1, E1, B> =
    KIO.FlatMap(this, f)

/**
 * Flattens the current `KIO<R, E, KIO<R, E, A>>`.
 *
 * @param f a function, which will be applied to a successful value of type [A]
 * @return a new [KIO]
 */
fun <R, R1: R, E, E1: E, E2: E, A> KIO<R, E1, KIO<R1, E2, A>>.flatten(): KIO<R1, E, A> =
    andThen { it }

/**
 * Returns a [KIO], which runs this [KIO], followed by passing its
 * value into the function [f] and returning the resulting [KIO] if [A] is not null.
 *
 * @param f a function, which will be applied to a successful value of type [A]
 * @return a new [KIO]
 */
infix fun <R, R1: R, E: E1, E1, A, B> KIO<R, E, A?>.andThenNotNull(f: (A) -> KIO<R1, E1, B?>): KIO<R1, E1, B?> =
    andThen {
        if (it == null) {
            KIO.ok(null)
        } else {
            f(it)
        }
    }

/**
 * Returns a [KIO], which runs this [KIO], followed by passing its
 * value into the function [f] and returning a new [KIO] which had its value mapped.
 *
 * @param f a function, which will be applied to a successful value of type [A]
 * @return a new [KIO]
 */
infix fun <R, R1: R, E: E1, E1, A, B> KIO<R, E, A?>.mapNotNull(f: (A) -> B): KIO<R1, E1, B?> =
    map { it?.let(f) }

/**
 * Returns a [KIO], which runs [onSuccess] on a successful value
 * and [onFailure] on an error value.
 *
 * @param onSuccess a function applied to a successful computation
 * @param onFailure a function applied to a failed computation
 * @return a new [KIO]
 */
fun <R, R1: R, E1: E2, E2, A, B> KIO<R, E1, A>.fold(
    onFailure: (E1) -> B,
    onSuccess: (A) -> B
): KIO<R1, E2, B> =
    foldM({ KIO.ok(onFailure(it)) }, { KIO.ok(onSuccess(it)) })


/**
 * Returns a [KIO], which runs [onSuccess] on a successful value
 * and [onFailure] on an error value.
 *
 * @param onSuccess a function applied to a successful computation
 * @param onFailure a function applied to a failed computation
 * @return a new [KIO]
 */
fun <R, R1: R, E1, E2, A, B> KIO<R, E1, A>.foldM(
    onFailure: (E1) -> KIO<R1, E2, B>,
    onSuccess: (A) -> KIO<R1, E2, B>,
): KIO<R1, E2, B> =
    foldCauseM(
        onFailure = { cause ->
            cause.fold(
                onPanic = {
                    // This cast is safe, because in a panic case
                    // there is no error of type E1, which means,
                    // we can safely cast it.
                    @Suppress("UNCHECKED_CAST")
                    KIO.halt(cause as Cause<E2>)
                },
                onExpected = { onFailure(it) },
            )
        },
        onSuccess = onSuccess
    )


/**
 * Returns a [KIO], which runs the handler [h] on an error.
 *
 * @param h a handler, which will be invoked on the error [E].
 * @return a new [KIO]
 */
@Deprecated(message = "catchError has been deprecated in favor of recover.", replaceWith = ReplaceWith("recover(h)"))
fun <R, E, E1, A> KIO<R, E, A>.catchError(
    h: (E) -> KIO<R, E1, A>
): KIO<R, E1, A> =
    foldM(h, KIO.Companion::ok)


/**
 * Returns a [KIO], which runs [onSuccess] on a successful value
 * and [onFailure] on any error, including interpreter errors.
 *
 * @param onSuccess a function applied to a successful computation
 * @param onFailure a function applied to a failed computation
 * @return a new [KIO]
 */
fun <R, R1: R, E1, E2, A, B> KIO<R, E1, A>.foldCauseM(
    onFailure: (Cause<E1>) -> KIO<R1, E2, B>,
    onSuccess: (A) -> KIO<R1, E2, B>,
): KIO<R1, E2, B> =
    KIO.Fold(this, onSuccess, onFailure)

/**
 * Returns either a value of type [A] if [A] is not null
 * or runs the new action produced by [f].
 *
 * @param f a function, which returns a new [KIO] to be run, if [A] is null.
 */
@Deprecated("Deprecated in favor of KIO.failOnNull", replaceWith = ReplaceWith("KIO.failOnNull(this, f)"))
fun <R, E, A> A?.onNullDo(f: () -> KIO<R, E, A>): KIO<R, E, A> =
    if (this == null) f() else IO.ok(this)

/**
 * Returns a [KIO], which catches an error [E] and
 * runs [other].
 *
 * @param other
 * @return a new [KIO]
 */
@Deprecated("Deprecated in favor of onNull", replaceWith = ReplaceWith("onNull(other)"))
infix fun <R, R1: R, E: E1, E1, A> KIO<R, E, A?>.orOnNull(
    other: () -> KIO<R1, E1, A>
): KIO<R1, E1, A> = andThen<R, R1, E, E1, A?, A> {
    if (it == null) {
        other()
    }
    else {
        KIO.ok(it)
    }
}


/**
 * Returns a [KIO], which attempts to run the computation and
 * returns an error as a [Result].
 *
 * This combinator is very useful for computations, that may fail
 * and where you just want to log the failure.
 *
 * @return a new [KIO]
 */
fun <R, E, A> KIO<R, E, A>.attempt(): KIO<R, Nothing, Result<E, A>> =
    foldM(
        onSuccess = { KIO.ok(Result.Ok(it)) },
        onFailure = { KIO.ok(Result.Err(it)) }
    )


/**
 * Returns a [KIO], which will run every [KIO] in the list and
 * bail on the first error.
 *
 * @return a new [KIO]
 */
fun <R, E, A> Iterable<KIO<R, E, A>>.collect(): KIO<R, E, List<A>> =
    fold(KIO.ok(listOf())) { result: KIO<R, E, List<A>>, next ->
        result.andThen { xs: List<A> ->
            next.map { xs + listOf(it) }
        }
    }

/**
 * Returns a [KIO], which will run every [KIO] in the list and
 * bail on the first error.
 *
 * @return a new [KIO]
 */
fun <R, E, A, B> Iterable<A>.collectBy(f: (A) -> KIO<R, E, B>): KIO<R, E, List<B>> =
    fold(KIO.ok(mutableListOf())) { result: KIO<R, E, List<B>>, next ->
        result.andThen { xs: List<B> ->
            f(next).map { xs + listOf(it) }
        }
    }


/**
 * Returns a [KIO], which will collect
 *
 * @param f
 * @return
 */
@Deprecated("Deprecated in favor of collectBy", replaceWith = ReplaceWith("collectBy(f)"))
fun <R, E, A, B> Iterable<A>.forEachM(f: (A) -> KIO<R, E, B>): KIO<R, E, List<B>> =
    map(f).collect()


/**
 * Returns a new [KIO], which flips error and success channels.
 *
 * @return a new [KIO] with flipped value and error
 */
fun <R, E, A> KIO<R, E, A>.flip(): KIO<R, A, E> =
    foldM(onSuccess = { KIO.fail(it) }, onFailure = { KIO.ok(it) })


/**
 * Returns a new [KIO], which will run [this], when the given [condition]
 * evaluates to true.
 *
 * @param condition any boolean condition
 * @return a new [KIO]
 */
fun <R, E, A> KIO<R, E, A>.guard(condition: Boolean): KIO<R, E, Unit> =
    if (condition) map { } else KIO.unit

/**
 * Returns a new [KIO], which will run [transform], when the given [condition]
 * evaluates to true.
 *
 * @param condition any boolean condition, based on the evaluated value
 * @param transform
 * @return a new [KIO]
 */
fun <R, E: E1, E1, A: A1, A1> KIO<R, E, A>.failIf(
    condition: (A) -> Boolean,
    transform: (A) -> E1
): KIO<R, E1, A1> =
    andThen {
        if (condition(it))
            KIO.fail(transform(it))
        else
            KIO.ok(it)
    }

/**
 * Returns a new [KIO] by combining the two KIOs with the given [f].
 *
 * @param other a second effect
 * @param f a function to combine the two
 * @return a new [KIO]
 */
fun <R, R1: R, E: E1, E1, A, B, C> KIO<R, E, A>.zipWith(
    other: KIO<R1, E1, B>,
    f: (A, B) -> C
): KIO<R1, E1, C> =
    andThen { a -> other.map { b -> f(a, b) } }


/**
 * Returns a new [KIO] by combining the two KIOs into a [Pair].
 *
 * @param other a second effect
 * @return a new [KIO]
 */
fun <R, R1: R, E: E1, E1, A, B> KIO<R, E, A>.zip(
    other: KIO<R1, E1, B>
): KIO<R1, E1, Pair<A, B>> =
    zipWith(other, ::Pair)


/**
 * Returns a new [KIO], which summarizes this [KIO].
 *
 * @param summary an action, which will be run before and after this [KIO].
 * @param f combine the two results of the [summary]
 * @return a new [KIO]
 */
fun <R, R1: R, E: E1, E1, A, B, C> KIO<R, E, A>.summarized(
    summary: KIO<R1, E1, B>,
    f: (B, B) -> C
): KIO<R1, E1, Pair<C, A>> =
    summary.andThen { start ->
        andThen { value ->
            summary.map { end -> f(start, end) to value }
        }
    }


/**
 * Returns a new [KIO], which measures the duration for current [KIO].
 *
 * @return new [KIO]
 */
fun <R, E, A> KIO<R, E, A>.measured(): KIO<R, E, Pair<Duration, A>> =
    summarized(KIO.effectTotal { System.currentTimeMillis() }) {
        start, end -> Duration.ofMillis(end - start)
    }


/**
 * Returns a new [KIO], which tries to cast the given [Throwable] into [E]. Dies
 * when it is not possible.
 *
 * @param clazz any error class
 * @return a new [KIO]
 */
fun <R, E: Throwable, A> KIO<R, Throwable, A>.refineOrDie(clazz: KClass<E>): KIO<R, E, A> =
    recover {
        if (clazz.java.isAssignableFrom(it::class.java))
            @Suppress("UNCHECKED_CAST")
            KIO.fail(it as E)
        else
            throw it
    }


/**
 * Returns a new [KIO], which halts, when the given error has occurred. This
 * is very useful for cases, where the error was fatal.
 *
 * @return a new [KIO]
 */
fun <R, E: Throwable, A> KIO<R, E, A>.orDie(): URIO<R, A> =
    recover { throw it }


/**
 * Returns a new [KIO], which runs this action to completion and retrieves
 * the result as an [Exit].
 *
 * @return a new [KIO]
 */
fun <R, E, A> KIO<R, E, A>.run(): URIO<R, Exit<E, A>> =
    foldCauseM(
        onFailure = { cause -> KIO.ok(Exit.fail(cause)) },
        onSuccess = { value -> KIO.ok(Exit.ok(value)) }
    )

/**
 * A version of [KIO.bracket], which ignores the acquired resource.
 *
 * @param release a function releasing the resource opened by [this] action
 * @param use a function using the resource opened by [this] action.
 * @return a new [KIO]
 */
fun <R, E, A, B> KIO<R, E, A>.bracketIgnore(
    release: URIO<R, Any?>,
    use: KIO<R, E, B>
): KIO<R, E, B> =
    KIO.bracket(this, { release }) { use }


/**
 * Returns a [KIO], which runs the handler [h] on an error.
 *
 * @param h a handler, which will be invoked on the error [E].
 * @return a new [KIO]
 */
infix fun <R, E, E1, A> KIO<R, E, A>.recover(
    h: (E) -> KIO<R, E1, A>
): KIO<R, E1, A> =
    foldM(h, KIO.Companion::ok)


/**
 * Returns a [KIO], which runs the handler [h] on an error.
 *
 * # Example
 *
 * This function is especially useful, if you also want to handle any
 * exception.
 *
 *
 * @param h a handler, which will be invoked on the error [E].
 * @return a new [KIO]
 */
infix fun <R, E: E1, E1, A> KIO<R, E, A>.recoverCause(
    h: (Cause<E>) -> KIO<R, E1, A>
): KIO<R, E1, A> =
    foldCauseM(h, KIO.Companion::ok)


/**
 * Returns a [KIO], which runs the handler [h] on an error.
 *
 * @param h a handler, which will be invoked on the error [E].
 * @return a new [KIO]
 */
infix fun <R, E: E1, E1, A> KIO<R, E, A>.recoverDefault(
    h: () -> A
): KIO<R, E1, A> =
    recover { KIO.ok(h()) }


/**
 * Returns a [KIO], which runs the handler [h] on an error.
 *
 * @param h a handler, which will be invoked on the error [E].
 * @return a new [KIO]
 */
fun <R, E: E1, E1, A> KIO<R, E, A?>.onNull(
    h: () -> KIO<R, E1, A>,
): KIO<R, E1, A> =
    andThen {
        if (it == null)
            h()
        else
            KIO.ok(it)
    }

/**
 * Returns a [KIO], which runs the handler [h] on an error.
 *
 * @param h a handler, which will be invoked on the error [E].
 * @return a new [KIO]
 */
infix fun <R, R1: R, E: E1, E1, A> KIO<R, E, A?>.onNullFail(
    h: () -> E1,
): KIO<R, E1, A> =
    onNull { KIO.fail(h()) }


/**
 * Returns a [KIO], which runs the handler [h] on an error.
 *
 * @param h a handler, which will be invoked on the error [E].
 * @return a new [KIO]
 */
fun <R, E: E1, E1, A> KIO<R, E, A?>.onNullDefault(
    h: () -> A,
): KIO<R, E1, A> =
    onNull { KIO.ok(h()) }