package de.lambda9.tailwind.jooq

import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.KIOException
import de.lambda9.tailwind.core.Runtime
import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import org.jooq.DSLContext
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.concurrent.Semaphore
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Testcontainers
@Disabled
class KIOTransactionTest {

    private val person = DSL.table("person")
    private val firstName = DSL.field("first_name", String::class.java)
    private val lastName = DSL.field("last_name", String::class.java)
    private val age = DSL.field("age", Int::class.java)
    private val dsl = DSL.using(postgres.jdbcUrl, postgres.username, postgres.password)
    private val runtime = Runtime.Companion.new(Jooq(dsl, Unit))

    @BeforeEach
    fun before() {
        val flyway = Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .cleanDisabled(false)
            .locations("classpath:/db.migration")
            .load()

        flyway.clean()
        flyway.migrate()

    }

    @Test
    fun `transact stores a person in the database`() {
        val kio = Jooq.query {
            insertPerson()
            selectFrom(person).fetch()
        }

        val result = runtime.unsafeRun(kio.transact())
        assertEquals(1, result.size)
        assertEquals("Test", result[0][firstName])
        assertEquals("Bla", result[0][lastName])
    }

    @Test
    fun `transact stores nothing in the database on rollback`() {
        val kio = Jooq.query {
            insertPerson()
            val result = selectFrom(person).fetch()
            assertEquals(1, result.size)
            throw DataAccessException("Throw to rollback this one")
        }

        val result = runtime.unsafeRunSync(kio.transact())
        assertTrue(result.fold({ true }, { false }), "Exit has failed")

        val check = Jooq.query { selectFrom(person).fetch() }

        val allPersons = unsafeRunAndLog(check)
        assertEquals(0, allPersons.size)
    }

    private fun <A> unsafeRunAndLog(kio: JIO<A>): A {
        val exit = runtime.unsafeRunSync(kio)
        return exit.fold(
            onError = { cause ->
                cause.defects().forEach {
                    logger.error(it) { "Something failed" }
                }

                cause.failures().forEach {
                    logger.error(it) {  }
                }

                throw KIOException(cause)
            },
            onSuccess = {
                it
            }
        )
    }

    // This has been disabled, since sometimes this would completely halt execution of
    // the build on our build server. I am not sure why, but this should be run
    // periodically on the dev system.
    @Test
    fun `different transactions can't see each other's data before commit`() {

        val t2ShouldLookAndSeeNothing = Semaphore(0)
        val t1ShouldCommit = Semaphore(0)
        val t2ShouldLookAndSeePerson = Semaphore(0)

        val t1 = Thread {
            val kio = Jooq.query {
                insertPerson()
                val result = selectFrom(person).fetch()
                assertEquals(1, result.size)
                println("T1: Notify Main about inserted person")
                t2ShouldLookAndSeeNothing.release()
                println("T1: Waiting for Main's commit signal")
                t1ShouldCommit.acquire()
                println("T1: Got Main's commit signal, committing transaction")
            }
            val result = runtime.unsafeRunSync(kio.transact())
            t2ShouldLookAndSeePerson.release()
        }

        t1.start()

        val kio = Jooq.query {
            val resultBeforeCommit = selectFrom(person)
                .fetch()
            assertEquals(0, resultBeforeCommit.size)
            if (resultBeforeCommit.isNotEmpty) {
                println("Stop thread after failure")
                t1ShouldCommit.release()
                throw Exception("No person should have been fetched!")
            } else {
                println("Main: Notify T1 to commit its transaction now")
                t1ShouldCommit.release()
                println("Main: Waiting for notification from T1 about inserted person and commit")
                t2ShouldLookAndSeePerson.acquire()
                val resultAfterCommit = selectFrom(person).fetch()
                assertEquals(1, resultAfterCommit.size)
            }
        }
        println("Main: Waiting for notification from T1 about inserted person")
        t2ShouldLookAndSeeNothing.acquire()
        println("Main: Got signal from T1, looking for persons in DB")
        val result = Runtime.Companion.new(
            Jooq(
                DSL.using(postgres.jdbcUrl, postgres.username, postgres.password), Unit
            )
        ).unsafeRunSync(kio.transact())
        assertTrue(result.fold({ false }, { true }), "Exit has succeeded")

        t1.join()
    }

    @Test
    fun `nested transacts run independent of each other`() {
        val kio = KIO.Companion.comprehension<Jooq<Any?>, DataAccessException, Unit> {
            val shouldBeInserted = !Jooq.query {
                insertPerson()
            }.transact()

            !Jooq.query {
                insertPerson()
                val result = selectFrom(person).fetch()
                assertEquals(1, result.size)
                throw DataAccessException("Throw to rollback this one")
            }
        }

        val result = runtime.unsafeRunSync(kio.transact())
        assertTrue(result.fold({ true }, { false }), "Exit has failed")

        val check = Jooq.query { selectFrom(person).fetch() }

        val allPersons = unsafeRunAndLog(check)
        // There should be at least one person from the nested transaction
        assertEquals(1, allPersons.size)
    }

    private fun DSLContext.insertPerson() {
        insertInto(person)
            .set(firstName, "Test")
            .set(lastName, "Bla")
            .set(age, 12)
            .execute()
    }

    companion object {

        @Container
        val postgres = PostgreSQLContainer<Nothing>("postgres:16")

        private val logger = KotlinLogging.logger {  }

    }

}