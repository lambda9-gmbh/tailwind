package de.lambda9.tailwind.core.extensions.exit

import de.lambda9.tailwind.core.Cause
import de.lambda9.tailwind.core.Exit
import de.lambda9.tailwind.core.IO
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.unsafeRunSync

import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class ExitExtensionsTest {

    @Test
    fun fold() {
        val kio = KIO.ok(5)
        val result = kio.unsafeRunSync().fold(
            onError = { 1 },
            onDefect = { 2 },
            onSuccess = { 3 },
        )
        assertEquals(3, result)
    }

    @Test
    fun `fold should work on defect`() {
        val kio = KIO.halt(Cause.panic(IllegalArgumentException("Woops")))
        val result = kio.unsafeRunSync().fold(
            onError = { 1 },
            onDefect = { 2 },
            onSuccess = { 3 },
        )

        assertEquals(2, result)
    }

    @Test
    fun `fold should work on error`() {
        val kio = KIO.fail(6)
        val result = kio.unsafeRunSync().fold(
            onError = { 1 },
            onDefect = { 2 },
            onSuccess = { 3 },
        )

        assertEquals(1, result)
    }

    @Test
    fun `getOrNullWithLoggedError should return null`() {
        val kio: IO<Int, String> = KIO.fail(6)
        val result = kio.unsafeRunSync().getOrNullLogError {  }
        assertNull(result)
    }

    @Test
    fun `getOrThrow should throw actual exception`() {
        val kio: IO<Int, String> = KIO.done(Exit.panic(IllegalStateException("Something")))
        assertThrowsExactly(IllegalStateException::class.java) {
            kio.unsafeRunSync().getOrThrow()
        }
    }

    @Test
    fun `getOrThrow should throw failure, if it is a throwable`() {
        val kio: IO<Throwable, String> = KIO.fail(IllegalStateException("Something"))
        assertThrowsExactly(IllegalStateException::class.java) {
            kio.unsafeRunSync().getOrThrow()
        }
    }

}