package de.lambda9.tailwind.core

import de.lambda9.tailwind.core.value
import org.junit.jupiter.api.Test
import kotlin.run
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppTest {


    @Test
    fun `Prüfe, ob die Exception korrekt gefangen wird`() {
        val fehler = "Das ist falsch"
        val xm: App<Unit, String, Int> = App.comprehension {
            of(5)
        }

        val ym: App<Unit, String, Int> = App.comprehension {
            throwError(fehler)
        }

        val zm: App<Unit, String, Int> = App.comprehension {
            val y = !ym
            val x = !xm

            of(x * y)
        }


        val result = zm.run(Unit)

        assertTrue { result is Result.Err }
        assertTrue { (result as Result.Err).error == fehler }
    }


    @Test
    fun `Prüfe, ob das catchError die interne Exception korrekt fängt`() {
        val fehler = "Das ist falsch"
        val xm: App<Unit, String, Int> = App.comprehension {
            of(5)
        }

        val ym: App<Unit, String, Int> = App.comprehension {
            throwError(fehler)
        }

        val zm: App<Unit, String, Int> = App.comprehension {
            val y = !ym.catchError { of(5) }
            val x = !xm

            of(x * y)
        }


        val result = zm.run(Unit)

        assertTrue { result is Result.Ok }
        assertTrue { (result as Result.Ok).value == 25 }
    }


    @Test
    fun `Prüfe, ob eine Fehlerhafte Ausführung stattfindet`() {
        val fehler = "Das ist falsch"
        val xm: App<Unit, String, Int> = App.comprehension {
            of(5)
        }

        val ym: App<Unit, String, Int> = App.comprehension {
            throwError(fehler)
        }

        val zm: App<Unit, String, Int> = App.comprehension {
            val y = !ym.map { !xm * it }.catchError { of(5) }

            of(y)
        }


        val result = zm.run(Unit)

        // Erwartet werden müsste an dieser Stelle eigentlich
        // Result.Err, weil das xm.returning()!
        assertTrue { result is Result.Ok }
        assertEquals((result as Result.Ok).value, 5)
    }

    sealed class Fehler {
        data class Eins(val a: Int = 0): Fehler()
        data class Zwei(val b: Int = 0): Fehler()
    }

    //@Test
    //@Ignore
    //fun testCompilation() {
    //    val y: App<Unit, Int, Int> = TODO()
    //    val b: App<Unit, Nothing, Int> = TODO()

    //    val x: App<Unit, Fehler, Int> = monadic {
    //        val v = !y.mapError { Fehler.Zwei(it) }
    //        val a = !b

    //        of(v)
    //    }

    //}

}