package de.lambda9.tailwind.core.extensions.kio

import de.lambda9.tailwind.core.*
import de.lambda9.tailwind.core.KIO.Companion.unsafeRunSync
import de.lambda9.tailwind.core.extensions.exit.getOrThrow
import de.lambda9.tailwind.core.extensions.kio.onNull
import org.junit.jupiter.api.DisplayName
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail


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
            KIO.fail(5).recover { KIO.ok(it * 5) }
        )

        assertEquals(25, result)
    }

    @Test
    fun `catchError does not call handler on success`() {
        val result = runtime.unsafeRun(KIO.ok(5)
            .recover { KIO.ok(25) })

        assertEquals(5, result)
    }

    @Test
    fun `recover does not call handler on success`() {
        val result = runtime.unsafeRun(KIO.ok(5)
            .recover { KIO.ok(25) })

        assertEquals(5, result)
    }

    @Test
    fun `orElseOnNull succeeds with a different value if the value is null`() {
        val kio: UIO<Int?> = KIO.ok(null)
        val result = runtime.unsafeRun(kio.onNull { KIO.ok(5) })
        assertEquals(5, result)
    }

    @Test
    fun `orElseOnNull does not call other, when the value is not null`() {
        val kio: UIO<Int?> = KIO.ok(5)
        val result = runtime.unsafeRun(kio.onNull { KIO.ok(10) })
        assertEquals(5, result)
    }

    @Test
    fun `sequence evals from left to right`() {
        val given = (0..10).toList()

        val result = runtime.unsafeRun(given
            .map { KIO.ok(it) }
            .sequence())

        assertEquals(given, result)
    }

    @Test
    @DisplayName("traverse(f) == map(f).sequence()")
    fun `traverse equals map(f) sequence`() {
        val f: (Int) -> UIO<Int> = { KIO.ok(it) }

        val given = (0..100)
            .map { Random.Default.nextInt() }
            .toList()

        val a = runtime.unsafeRun(given.traverse(f))
        val b = runtime.unsafeRun(given.map(f).sequence())
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
                println("Hello World!")
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

    /**
     * A helper to extract an error value from a Cause.
     * (Assumes that your Cause type provides a `fold` method.)
     */
    private fun <E> Cause<E>.getError(): E =
        fold(
            onPanic = { throw Exception("Unexpected panic: $it") },
            onExpected = { it }
        )

    @Test
    fun testAndThenSuccess() {
        // KIO.ok(10) andThen { 10 * 2 } should produce 20.
        val computation = KIO.ok(10) andThen { a -> KIO.ok(a * 2) }
        val exit = computation.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals(20, exit.value)
            is Exit.Failure -> fail("Expected success, got failure: ${exit.error}")
        }
    }

    @Test
    fun testAndThenFailure() {
        // Chaining on a failure should leave the failure unchanged.
        val computation = KIO.fail("error") andThen { a: Int -> KIO.ok(a * 2) }
        val exit = computation.unsafeRunSync()
        when (exit) {
            is Exit.Success -> fail("Expected failure, got success: ${exit.value}")
            is Exit.Failure -> assertEquals("error", exit.error.getError())
        }
    }

    @Test
    fun testFoldSuccess() {
        // fold transforms a success value.
        val computation = KIO.ok(5).fold(
            onFailure = { err -> "failed: $err" },
            onSuccess = { value -> "success: $value" }
        )
        val exit = computation.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals("success: 5", exit.value)
            is Exit.Failure -> fail("Expected success, got failure")
        }
    }

    @Test
    fun testFoldFailure() {
        // fold transforms a failure value (the error is “recovered” via ok).
        val computation = KIO.fail("oops").fold(
            onFailure = { err -> "failed: $err" },
            onSuccess = { value -> "success: $value" }
        )
        val exit = computation.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals("failed: oops", exit.value)
            is Exit.Failure -> fail("Expected success (via fold), got failure")
        }
    }

    @Test
    fun testFoldMSuccess() {
        val computation = KIO.ok(7).foldM(
            onFailure = { err -> KIO.ok("failed: $err") },
            onSuccess = { value -> KIO.ok("success: $value") }
        )
        val exit = computation.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals("success: 7", exit.value)
            is Exit.Failure -> fail("Expected success, got failure")
        }
    }

    @Test
    fun testFoldMFailure() {
        val computation = KIO.fail("error").foldM(
            onFailure = { err -> KIO.ok("failed: $err") },
            onSuccess = { value -> KIO.ok("success: $value") }
        )
        val exit = computation.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals("failed: error", exit.value)
            is Exit.Failure -> fail("Expected success (via foldM), got failure")
        }
    }

    @Test
    fun testAttemptSuccess() {
        // attempt should wrap a successful value in a Result.Ok.
        val computation = KIO.ok(42).attempt()
        val exit = computation.unsafeRunSync()
        when (exit) {
            is Exit.Success -> when (val result = exit.value) {
                is Result.Ok -> assertEquals(42, result.value)
                is Result.Err -> fail("Expected Result.Ok, got Result.Err")
            }
            is Exit.Failure -> fail("Expected success, got failure")
        }
    }

    @Test
    fun testAttemptFailure() {
        // attempt should catch the error and return it in a Result.Err.
        val computation = KIO.fail("fail").attempt()
        val exit = computation.unsafeRunSync()
        when (exit) {
            is Exit.Success -> when (val result = exit.value) {
                is Result.Err -> assertEquals("fail", result.error)
                is Result.Ok -> fail("Expected Result.Err, got Result.Ok")
            }
            is Exit.Failure -> fail("attempt should never fail")
        }
    }

    @Test
    fun testCollectSuccess() {
        // collect runs a list of successful KIO actions and aggregates the results.
        val computations = listOf(KIO.ok(1), KIO.ok(2), KIO.ok(3))
        val collected = computations.sequence()
        val exit = collected.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals(listOf(1, 2, 3), exit.value)
            is Exit.Failure -> fail("Expected success, got failure")
        }
    }

    @Test
    fun testCollectFailure() {
        // If any action fails, collect should bail immediately.
        val computations = listOf(KIO.ok(1), KIO.fail("error"), KIO.ok(3))
        val collected = computations.sequence()
        val exit = collected.unsafeRunSync()
        when (exit) {
            is Exit.Success -> fail("Expected failure, got success")
            is Exit.Failure -> assertEquals("error", exit.error.getError())
        }
    }

    @Test
    fun testCollectBy() {
        // collectBy maps each element to a KIO and collects the results.
        val list = listOf(1, 2, 3)
        val computation = list.traverse { a -> KIO.ok(a * 10) }
        val exit = computation.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals(listOf(10, 20, 30), exit.value)
            is Exit.Failure -> fail("Expected success, got failure")
        }
    }

    @Test
    fun testFlip() {
        // flip turns a success into a failure and vice-versa.
        val successFlipped = KIO.ok("ok").flip()
        val failureFlipped = KIO.fail("fail").flip()

        val exit1 = successFlipped.unsafeRunSync()
        when (exit1) {
            is Exit.Success -> fail("Expected failure (from a successful value flipped)")
            is Exit.Failure -> assertEquals("ok", exit1.error.getError())
        }

        val exit2 = failureFlipped.unsafeRunSync()
        when (exit2) {
            is Exit.Success -> assertEquals("fail", exit2.value)
            is Exit.Failure -> fail("Expected success (from a failed value flipped)")
        }
    }

    @Test
    fun testGuard() {
        // guard returns Unit regardless; it runs the mapping if the condition is true,
        // otherwise returns KIO.unit.
        val compTrue = KIO.ok(123).guard(true)
        val exitTrue = compTrue.unsafeRunSync()
        when (exitTrue) {
            is Exit.Success -> assertEquals(Unit, exitTrue.value)
            is Exit.Failure -> fail("Expected success, got failure")
        }

        val compFalse = KIO.ok(123).guard(false)
        val exitFalse = compFalse.unsafeRunSync()
        when (exitFalse) {
            is Exit.Success -> assertEquals(Unit, exitFalse.value)
            is Exit.Failure -> fail("Expected success, got failure")
        }
    }

    @Test
    fun testFailIf() {
        // failIf should produce a failure when the condition is met.
        val compFailure = KIO.ok(10).failIf({ it > 5 }) { a -> "too high: $a" }
        val exitFailure = compFailure.unsafeRunSync()
        when (exitFailure) {
            is Exit.Success -> fail("Expected failure, got success")
            is Exit.Failure -> assertEquals("too high: 10", exitFailure.error.getError())
        }

        // And when the condition is false, the original value is kept.
        val compSuccess = KIO.ok(3).failIf({ it > 5 }) { a -> "should not happen" }
        val exitSuccess = compSuccess.unsafeRunSync()
        when (exitSuccess) {
            is Exit.Success -> assertEquals(3, exitSuccess.value)
            is Exit.Failure -> fail("Expected success, got failure")
        }
    }

    @Test
    fun testZip() {
        // zip combines two KIO actions into a pair.
        val kio1 = KIO.ok(5)
        val kio2 = KIO.ok(10)
        val zipped = kio1.zip(kio2)
        val exit = zipped.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals(Pair(5, 10), exit.value)
            is Exit.Failure -> fail("Expected success, got failure")
        }
    }

    @Test
    fun testSummarized() {
        // summarized runs a summary action before and after the main action.
        val comp = KIO.ok(42).summarized(KIO.ok("s")) { start, end -> "$start -> $end" }
        val exit = comp.unsafeRunSync()
        when (exit) {
            is Exit.Success -> {
                val (summary, value) = exit.value
                assertEquals("s -> s", summary)
                assertEquals(42, value)
            }
            is Exit.Failure -> fail("Expected success, got failure")
        }
    }

    @Test
    fun testMeasured() {
        // measured should report a (non-negative) duration along with the value.
        val comp = KIO.effectTotal { 100 }.measured()
        val exit = comp.unsafeRunSync()
        when (exit) {
            is Exit.Success -> {
                val (duration, value) = exit.value
                assertTrue(duration.toMillis() >= 0, "Duration should be non-negative")
                assertEquals(100, value)
            }
            is Exit.Failure -> fail("Expected success, got failure")
        }
    }

    @Test
    fun testRecover() {
        // recover should catch a failure and convert it to a success.
        val comp = KIO.fail("error").recover { err -> KIO.ok("recovered from $err") }
        val exit = comp.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals("recovered from error", exit.value)
            is Exit.Failure -> fail("Expected recovery, got failure")
        }
    }

    @Test
    fun testRecoverDefault() {
        // recoverDefault should supply a default value when there is a failure.
        val comp = KIO.fail("oops").recoverDefault { "default" }
        val exit = comp.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals("default", exit.value)
            is Exit.Failure -> fail("Expected default recovery, got failure")
        }
    }

    @Test
    fun testRecoverCause() {
        // recoverCause uses the full Cause to recover.
        val comp = KIO.fail("oops").recoverCause { cause ->
            cause.fold(
                onPanic = { KIO.halt(cause) },
                onExpected = { KIO.ok("recovered from $it") }
            )
        }
        val exit = comp.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals("recovered from oops", exit.value)
            is Exit.Failure -> fail("Expected recovery via recoverCause, got failure")
        }
    }

    @Test
    fun testOnNull() {
        // onNull should run the fallback if the value is null.
        val comp: IO<String, String?> = KIO.ok(null)
        val recovered = comp.onNull { KIO.ok("not null") }
        val exit = recovered.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals("not null", exit.value)
            is Exit.Failure -> fail("Expected success, got failure")
        }
    }

    @Test
    fun testOnNullFail() {
        // onNullFail should fail if the value is null.
        val comp: IO<String, String?> = KIO.ok(null)
        val recovered = comp onNullFail { "Null value" }
        val exit = recovered.unsafeRunSync()
        when (exit) {
            is Exit.Success -> fail("Expected failure due to null, got success")
            is Exit.Failure -> assertEquals("Null value", exit.error.getError())
        }
    }

    @Test
    fun testOnNullDefault() {
        // onNullDefault should supply a default if the value is null.
        val comp: IO<String, String?> = KIO.ok(null)
        val recovered = comp.onNullDefault { "default" }
        val exit = recovered.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals("default", exit.value)
            is Exit.Failure -> fail("Expected default value on null, got failure")
        }
    }

    @Test
    fun testRun() {
        // run returns an Exit wrapped in a KIO.
        val comp = KIO.ok(99)
        val exit = comp.run().unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals(99, exit.value.fold({ fail("Expected success, got $it") }, { it }))
            is Exit.Failure -> fail("Expected run() to succeed, got failure")
        }
    }

    @Test
    fun testBracketIgnore() {
        // bracketIgnore should ensure that the release action is executed.
        var released = false
        val acquire = KIO.ok("resource")
        val releaseAction = KIO.effectTotal { released = true }
        val use = KIO.ok("used")
        val comp = acquire.bracketIgnore(releaseAction, use)
        val exit = comp.unsafeRunSync()
        when (exit) {
            is Exit.Success -> assertEquals("used", exit.value)
            is Exit.Failure -> fail("Expected success, got failure")
        }
        assertTrue(released, "Resource should have been released")
    }

    @Test
    fun testRefineOrDie() {
        // refineOrDie should catch a failure of the expected type.
        class MyException(message: String) : Exception(message)

        val comp: KIO<Any, Throwable, Int> = KIO.fail(MyException("bad"))
        val refined: KIO<Any, MyException, Int> = comp.refineOrDie(MyException::class)
        val exit = refined.unsafeRunSync(Unit)
        when (exit) {
            is Exit.Success -> fail("Expected failure, got success")
            is Exit.Failure -> {
                val error = exit.error.getError()
                assertEquals("bad", error.message)
            }
        }
    }

    @Test
    fun testOrDie() {
        // orDie should rethrow the error. We expect an exception here.
        val comp: IO<Throwable, Int> = KIO.fail(Exception("fatal"))
        val urio = comp.orDie()
        assertFailsWith<Exception>("fatal") {
            urio.unsafeRunSync().getOrThrow()
        }
    }

}