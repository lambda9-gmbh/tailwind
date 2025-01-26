package de.lambda9.tailwind.core.extensions


/**
 * Chunk the given List by checking every element.
 *
 * @param f
 * @return a list of lists
 */
fun <A> List<A>.chunkedOnThreshold(condition: (A) -> Boolean): List<List<A>> {
    val result = mutableListOf<List<A>>()
    val buffer = mutableListOf<A>()

    for (element in this) {
        if (buffer.isNotEmpty() && condition(element)) {
            result.add(buffer.toList())
            buffer.clear()
        }
        buffer += element
    }
    if (buffer.isNotEmpty()) result.add(buffer.toList())

    return result
}


fun <T> Sequence<T>.chunkedOnThreshold(condition: (T) -> Boolean): Sequence<List<T>> = sequence {
    val buffer = mutableListOf<T>()

    for (element in this@chunkedOnThreshold) {
        if (buffer.isNotEmpty() && condition(element)) {
            yield(buffer.toList())
            buffer.clear()
        }
        buffer += element
    }
    if (buffer.isNotEmpty()) yield(buffer.toList())
}