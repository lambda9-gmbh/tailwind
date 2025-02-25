package de.lambda9.tailwind.core

/**
 * An interface for allowing the implementing class to
 * convert itself to the given Err.
 *
 * This interface has been introduced in this library, since
 * it became evident, that it would be introduced in every
 * project anyways.
 */
interface ToError<Err> {

    /**
     * Convert class to an error.
     */
    fun toError(): Err

    /**
     * Hook for possible logging of the given error when it is thrown.
     *
     * The default behavior is to do nothing. However, any application
     * may reimplement this method in its context to automatically
     * react on the event of throwing the error.
     *
     * Note 1: An implementation can never know, if the error was
     * caught later on in a catchError. Therefore, this should be
     * considered when logging errors which may be caught.
     *
     * Note 2: This hook is called on every throw, which may cause an
     * error to occur multiple times if this instance was rethrown.
     */
    fun logOnThrow(e: Err): Unit = Unit

}