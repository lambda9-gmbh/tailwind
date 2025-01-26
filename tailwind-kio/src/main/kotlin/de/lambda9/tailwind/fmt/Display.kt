package de.lambda9.tailwind.fmt

import java.io.Serializable

/**
 * [Display] is an experimental interface to
 *
 * ## Example
 *
 * ```kotlin
 * sealed class Err: Display {
 *
 *     data class X(val y: Long): Err()
 *
 *     override fun display(): Doc = when (this) {
 *         is X -> Doc.text("Something bad happened $y")
 *     }
 *
 * }
 * ```
 */
interface Display: Serializable {

    /**
     *
     * @return a new Doc
     */
    fun display(): Doc

}