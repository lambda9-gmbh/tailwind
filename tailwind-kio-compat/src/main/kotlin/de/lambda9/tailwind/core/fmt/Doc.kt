package de.lambda9.tailwind.core.fmt

import java.io.Serializable

/**
 * A [Doc] is an abstract data type, that represents a document.
 *
 * This abstract representation may then be used to render it to Text,
 * HTML, Markdown or any other format available.
 *
 * ## Example
 *
 * ```kotlin
 * sealed class AuthError: Display {
 *
 *     data class Invalid(val y: Long): Err()
 *
 *     override fun display(): Doc = when (this) {
 *         is X -> Doc.text("Something bad happened $y")
 *     }
 *
 * }
 * ```
 */
sealed class Doc: Serializable {

    internal data class Text(
        val content: String
    ): Doc()

    fun <B> fold(
        onText: (String) -> B,
    ): B = when (this) {
        is Text -> onText(content)
    }

    companion object {

        /**
         * Create a new [Doc] representing just the unaltered
         * given [input].
         *
         * @param input some form of text, that should be displayed to a user
         * @return
         */
        fun text(input: String): Doc =
            Text(input)

    }

}