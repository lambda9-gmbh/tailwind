package de.lambda9.tailwind.core.extensions.exit

import de.lambda9.tailwind.core.Cause
import de.lambda9.tailwind.core.Exit
import de.lambda9.tailwind.core.KIOException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.jvm.Throws


/**
 * Returns the successful value or the value returned by [f].
 *
 * @param f
 */
fun <E, A: A1, A1> Exit<E, A>.getOrElse(
    f: (Cause<E>) -> A1,
): A1 = fold(
    onSuccess = { it },
    onError = f,
)

/**
 * Returns the successful value or throws either
 * the actual [Throwable] which occurred or a
 * [KIOException], when a failure occurred.
 *
 * @return the value of type [A]
 */
@Throws(Throwable::class, KIOException::class)
fun <E, A> Exit<E, A>.getOrThrow(): A =
    getOrElse {
        val defect = it.firstDefectOrNull()
        if (defect != null) {
            throw defect
        }

        val failure = it.firstFailureOrNull()
        if (failure != null && failure is Throwable) {
            throw failure
        }

        throw KIOException(it)
    }


/**
 * Returns
 */
fun <E, A> Exit<E, A>.getOrNull(): A? =
    getOrElse { null }

/**
 * Returns the value of type [A] or null, if an error occurred, but logs the
 * actual error, which happened.
 *
 * @param message a message to identify the error further
 * @param f a function to compute the calling class and logger.
 */
fun <E, A> Exit<E, A>.getOrNullLogError(
    message: () -> String = { "An unexpected error occurred" },
    f: () -> Unit,
): A? = getOrElse {
    val name = f.javaClass.name
    val slicedName = when {
        name.contains("Kt$") -> name.substringBefore("Kt$")
        name.contains("$") -> name.substringBefore("$")
        else -> name
    }

    val logger = KotlinLogging.logger(slicedName)
    val defect = it.firstDefectOrNull()
    if (defect != null) {
        logger.warn(defect, message)
    }

    val failure = it.firstFailureOrNull()
    if (failure != null && failure is Throwable) {
        logger.warn(failure, message)
    }
    else if (failure != null) {
        logger.warn { "${message()}: '$failure'" }
    }

    null
}

/**
 * Returns a new value, handling the different cases of [Exit].
 *
 * This is a convenience function for manually folding over
 * the cause.
 *
 * ## Example
 *
 * ```kotlin
 * KIO.ok(5).unsafeRunSync(Unit).fold(
 *     onError = { log.warn { "This was expected" },
 *     onDefect = { log.error(it) { "Wooops" } },
 *     onSuccess = { log.info { "Everything is awesome $it" } }
 * )
 * ```
 *
 * @param onError a function, which handles the expected error case.
 * @param onDefect a function, which handles the unexpected error case.
 * @param onSuccess a function which handles the successful case.
 * @return a new value of type [B]
 */
inline fun <E, A, B> Exit<E, A>.fold(
    onError: (E) -> B,
    onDefect: (Throwable) -> B,
    onSuccess: (A) -> B,
): B = fold(
    onError = { it.fold(onError, onDefect) },
    onSuccess = onSuccess
)