package de.lambda9.tailwind.core

import de.lambda9.tailwind.core.extensions.exit.getOrElse
import de.lambda9.tailwind.core.internal.RunLoop
import java.io.Serializable


interface Runtime<R>: Serializable {

    val env: R

    /**
     * @param kio
     */
    fun <E, A> unsafeRun(kio: KIO<R, E, A>): A =
        unsafeRunSync(kio).getOrElse { throw KIOException(it) }

    /**
     *
     * @param kio
     */
    fun <E, A> unsafeRunSync(kio: KIO<R, E, A>): Exit<E, A> =
        RunLoop(env).unsafeRunSync(kio)


    companion object {

        fun <R> new(env: R): Runtime<R> = object: Runtime<R> {
            override val env: R get() = env
        }

    }

}