package de.lambda9.tailwind.core

import de.lambda9.tailwind.core.extensions.exit.getOrElse
import de.lambda9.tailwind.core.internal.RunLoop
import java.io.Serializable


interface Runtime<R>: Serializable {

    val env: R

    /**
     * Run the given [kio] value and throw a [KIOException] if
     * it errored.
     *
     * @param kio a value to be run
     * @return a value of type [A] if nothing failed.
     */
    @Throws(KIOException::class)
    fun <E, A> unsafeRun(kio: KIO<R, E, A>): A =
        unsafeRunSync(kio).getOrElse { throw KIOException(it) }

    /**
     * Run the given [kio] value and return an [Exit] containing
     * either an expected failure or an unexpected failure.
     *
     * @param kio a value to be run
     * @return a value of type [A] if nothing failed.
     */
    fun <E, A> unsafeRunSync(kio: KIO<R, E, A>): Exit<E, A> =
        RunLoop(env).unsafeRunSync(kio)


    companion object {

        /**
         * Create a new [Runtime].
         */
        fun <R> new(env: R): Runtime<R> = object: Runtime<R> {
            override val env: R get() = env
        }

    }

}