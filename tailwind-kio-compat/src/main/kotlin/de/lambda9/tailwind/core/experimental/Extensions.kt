package de.lambda9.tailwind.core.experimental

import de.lambda9.tailwind.core.AndThenScope
import de.lambda9.tailwind.core.App


/**
 * Provider another result, in case the computation yields a
 * nullable value and said value is null.
 *
 * Examples:
 *
 * ```kotlin
 * of(null)          orElseOnNull of(6)             == of(5)
 * throwError("err") orElseOnNull of(5)             == throwError("err")
 * throwError("")    orElseOnNull throwError("err") == throwError("err")
 * ```
 *
 * @param other another computation
 * @return If the result of this [App] is a [Result.Ok], the
 * value of the computation will be propagated, if it is [Result.Ok]
 * but null, [other] will be evaluated. If it contains an error,
 * the error will be propagated.
 */
@Deprecated(
    message = "The other is not a lambda, which may cause unnecessary instantiations. " +
        "There is an alternative implementation, which takes a lambda. " +
        "It should also be simply doable with .andThen { whenNull(it, throwError(...)) } ",
    replaceWith = ReplaceWith("use .orElseOnNull { throwError(...) }, or .andThen { whenNull(it, throwError(..)) }")
)
infix fun <Env, Error, A> App<Env, Error, A?>.orElseOnNull(other: App<Env, Error, A>): App<Env, Error, A> =
    orElseOnNull { other }


/**
 * Provider another result, in case the computation yields a
 * nullable value and said value is null.
 *
 * Examples:
 *
 * ```kotlin
 * of(null)          orElseOnNull of(6)             == of(5)
 * throwError("err") orElseOnNull of(5)             == throwError("err")
 * throwError("")    orElseOnNull throwError("err") == throwError("err")
 * ```
 *
 * @param other a function that provides another computation
 * @return If the result of this [App] is a [de.lambda9.tailwind.core.Result.Ok], the
 * value of the computation will be propagated, if it is [de.lambda9.tailwind.core.Result.Ok]
 * but null, [other] will be evaluated. If it contains an error,
 * the error will be propagated.
 */
infix fun <Env, Error, A> App<Env, Error, A?>.orElseOnNull(
    other: AndThenScope<Env, Error>.() -> App<Env, Error, A>
): App<Env, Error, A> = andThen {
    when (it) {
        null ->
            other()

        else ->
            App.of(it)
    }
}


/**
 * Starts a monadic action Block.
 *
 * @param f a function
 */
@Deprecated("This method was deprecated in favor of App.comprehension", ReplaceWith("App.comprehension"))
fun <Env, E, A> monadic(f: AndThenScope<Env, E>.() -> App<Env, E, A>): App<Env, E, A> =
    App.ask<Env, E>().andThen { env ->
        try {
            AndThenScope<Env, E>(env).f()
        } catch (ex: DangerouslyInternalAppException) {
            @Suppress("UNCHECKED_CAST")
            throwError<A>(ex.error as E)
        }
    }
