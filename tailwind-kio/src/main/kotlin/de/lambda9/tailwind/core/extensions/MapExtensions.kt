package de.lambda9.tailwind.core.extensions


/**
 * Returns a new [Map], which is the union of both maps. If a key is contained in both maps
 * it will be merged using the given [merge] function.
 *
 * @param other another Map
 * @param merge a merge function
 * @return a new Map
 */
fun <K, V> Map<K, V>.unionWith(other: Map<K, V>, merge: (K, V, V) -> V): Map<K, V> {
    val result = mutableMapOf<K, V>()
    result.putAll(this)
    other.forEach { k, v ->
        if (v != null) {
            result.merge(k, v) { _, c -> merge(k, c, v) }
        }
    }

    return result
}
