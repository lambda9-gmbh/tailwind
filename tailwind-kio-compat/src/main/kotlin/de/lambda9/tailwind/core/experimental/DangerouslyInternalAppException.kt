package de.lambda9.tailwind.core.experimental


/**
 * This Exception will be thrown, in a [monadic] or [jooqQuery] Block,
 * on encountering an error.
 */
internal class DangerouslyInternalAppException(val error: Any): Throwable()
