package de.lambda9.tailwind.jooq

import de.lambda9.tailwind.core.*
import de.lambda9.tailwind.core.extensions.kio.foldCauseM
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.jooq.impl.DefaultExecuteListenerProvider

/**
 * Run [KIO] inside a database transaction.
 *
 * There are two exceptional cases, which should be
 * considered here.
 *
 * 1. An exception occurs during the transaction.
 * 2. The final [Result] contains an error.
 *
 * In both cases, the transaction will be rolled back. In
 * the event of 1. the exception will be rethrown after
 * rolling back the transaction.
 *
 * @return a [Result] representing, whether
 * the computation failed or succeeded.
 * @throws Throwable Any throwable which may have been thrown
 * during execution.
 *
 * @author Matthias Metzger, Benjamin Klink, Sören Witt
 */
fun <R, E, A> KIO<Jooq<R>, E, A>.transact(): KIO<Jooq<R>, E, A> = KIO.accessM { env ->
    env.dsl.connectionResult { connection ->
        val autoCommit = connection.autoCommit
        connection.autoCommit = false

        val transactionalContext = DSL.using(connection)

        // TODO: Make this better, this is hideous.
        val kio = foldCauseM(
            onSuccess = { value ->
                connection.commit()
                connection.autoCommit = autoCommit
                KIO.ok(value)
            },
            onFailure = { cause ->
                connection.rollback()
                connection.autoCommit = autoCommit
                KIO.halt(cause)
            }
        )
        // We need to run this here, because everything with the connection
        // needs to be run in this connectionResult method, otherwise the
        // connection will have been closed already, before anything gets run
        // TODO: Look into working with the transaction manager
        Runtime.new(env.copy(dsl = transactionalContext))
            .unsafeRunSync(kio)
            .fold(onError = KIO.Companion::halt, onSuccess = KIO.Companion::ok)
    }
}
