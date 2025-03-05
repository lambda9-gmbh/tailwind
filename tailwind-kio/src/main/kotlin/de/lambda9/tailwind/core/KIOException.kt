package de.lambda9.tailwind.core


/**
 * Represents an Exception, that wraps a [Cause] and
 * can therefore be thrown from contexts, where communicating
 * exact failure reasons would not be possible otherwise.
 */
class KIOException(val error: Cause<Any?>): Exception()