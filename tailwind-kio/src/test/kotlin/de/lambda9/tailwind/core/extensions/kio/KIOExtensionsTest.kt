package de.lambda9.tailwind.core.extensions.kio

import de.lambda9.tailwind.core.*
import org.junit.jupiter.api.DisplayName
import java.io.IOException
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class KIOExtensionsTest {

    private val runtime = Runtime.new(Unit)

    @Test
    fun `fold invokes onSuccess callback on a successful computation`() {
        val result = runtime.unsafeRun(KIO.ok(5).foldM(
            onSuccess = { KIO.ok(it * 5) },
            onFailure = { throw IllegalArgumentException() }
        ))

        assertEquals(25, result)
    }

    @Test
    fun `fold invokes onError callback on a failed computation`() {
        val result = runtime.unsafeRun(KIO.fail(5).foldM(
            onSuccess = { throw IllegalArgumentException() },
            onFailure = { KIO.ok(it * 5) }
        ))

        assertEquals(25, result)
    }

    @Test
    fun `catchError calls handler on error`() {
        val result = runtime.unsafeRun(
            KIO.fail(5).catchError { KIO.ok(it * 5) }
        )

        assertEquals(25, result)
    }

    @Test
    fun `catchError does not call handler on success`() {
        val result = runtime.unsafeRun(KIO.ok(5)
            .catchError { KIO.ok(25) })

        assertEquals(5, result)
    }

    @Test
    fun `orElse does not call handler on success`() {
        val result = runtime.unsafeRun(KIO.ok(5)
            .or { KIO.ok(25) })

        assertEquals(5, result)
    }

    @Test
    fun `eventually succeeds after a few failures`() {
        var counter = 0
        val result = runtime.unsafeRun(KIO.ok(5)
            .andThen {
                counter++
                if (counter > 3) {
                    KIO.ok(5)
                }
                else {
                    KIO.fail(3)
                }
            }
            .eventually()
        )

        assertEquals(5, result)
    }

    @Test
    fun `orElseOnNull succeeds with a different value if the value is null`() {
        val kio: UIO<Int?> = KIO.ok(null)
        val result = runtime.unsafeRun(kio.orOnNull { KIO.ok(5) })
        assertEquals(5, result)
    }

    @Test
    fun `orElseOnNull does not call other, when the value is not null`() {
        val kio: UIO<Int?> = KIO.ok(5)
        val result = runtime.unsafeRun(kio.orOnNull { KIO.ok(10) })
        assertEquals(5, result)
    }

    @Test
    fun `sequence evals from left to right`() {
        val given = (0..10).toList()

        val result = runtime.unsafeRun(given
            .map { KIO.ok(it) }
            .collect())

        assertEquals(given, result)
    }

    @Test
    @DisplayName("traverse(f) == map(f).sequence()")
    fun `traverse equals map(f) sequence`() {
        val f: (Int) -> UIO<Int> = { KIO.ok(it) }

        val given = (0..100)
            .map { Random.Default.nextInt() }
            .toList()

        val a = runtime.unsafeRun(given.forEachM(f))
        val b = runtime.unsafeRun(given.map(f).collect())
        assertEquals(a, b)
    }

    @Test
    fun `flip creates value from error`() {
        val result = runtime.unsafeRun(KIO.fail(5).flip())
        assertEquals(5, result)
    }

    @Test
    fun `flip creates error from value`() {
        val result = runtime.unsafeRunSync(KIO.ok(5).flip())
        assert(result is Exit.Failure)
        assertEquals(5, (result as Exit.Failure).error.fold({it}, { throw it }))
    }

    @Test
    fun `attempt retrieves Ok on success`() {
        val result = runtime.unsafeRun(KIO.ok(5).attempt())
        assertTrue(result is Result.Ok)
    }

    @Test
    fun `attempt retrieves Ok on failure`() {
        val result = runtime.unsafeRun(KIO.fail(5).attempt())
        assertTrue(result is Result.Err)
    }

    @Test
    fun `orDie dies with the given exception`() {
        val x = KIO.effect { throw IllegalArgumentException() }
            .refineOrDie(IllegalArgumentException::class)
            .orDie()

        val exit = runtime.unsafeRunSync(x)
        assertTrue(exit is Exit.Failure)
        @Suppress("DEPRECATION")
        assertTrue(exit.error is Cause.Panic)
        assertTrue(exit.error.cause is IllegalArgumentException)
    }

    @Test
    fun `effect works in andThen`() {
        val a = KIO.effect { 5 }
        val composed = a.andThen {
            KIO.effect {
                Unit
            }
        }

        val exit = runtime.unsafeRunSync(composed)
        assertTrue(exit is Exit.Success)
    }

    @Test
    fun `single effect works`() {
        val a = KIO.effect { 5 }
        val exit = runtime.unsafeRunSync(a)
        assertTrue(exit is Exit.Success)
    }

    @Test
    fun `access retrieves the correct environment`() {
        val r = Runtime.new(5)
        val getEnv = KIO.access<Int>()
        val result = r.unsafeRun(getEnv)
        assertEquals(5, result)
    }

    @Test
    fun `access retrieves the current environment, when one was provided`() {
        val r = Runtime.new(5)
        val getEnv = KIO.access<Int>()
        @Suppress("DEPRECATION") val result = r.unsafeRun(getEnv.provide(8))
        assertEquals(8, result)
    }

}