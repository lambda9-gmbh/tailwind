package de.lambda9.tailwind.core.internal

import de.lambda9.tailwind.core.Cause
import de.lambda9.tailwind.core.Exit
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.bracketIgnore
import java.util.*


internal class RunLoop<R>(
    private val env: R
) {

    private val stack = Stack<(Any?) -> KIO<R, Any?, Any?>>()

    private val environments = Stack<Any?>().apply { push(env) }

    /**
     *
     * @param kio
     */
    @Suppress("UNCHECKED_CAST")
    fun <E, A> unsafeRunSync(kio: KIO<R, E, A>): Exit<E, A> =
        runHelp(kio) as Exit<E, A>

    private fun nextInstr(value: Any?): KIO<R, Any?, Any?>? =
        if (stack.isEmpty())
            null
        else
            stack.pop()(value)

    private fun unwindStack() {
        var unwinding = true
        while (unwinding && stack.isNotEmpty()) {
            when (val handler = stack.pop()) {
                is KIO.Fold<*, *, *, *, *> -> {
                    @Suppress("UNCHECKED_CAST")
                    val handle = handler.failure as (Any?) -> KIO<Any?, Any?, Any?>
                    stack.push(handle)
                    unwinding = false
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun runHelp(kio: KIO<R, Any?, Any?>): Exit<Any?, Any?> {
        var curKio: KIO<R, Any?, Any?>? = kio
        do {
            try {
                when (curKio) {
                    is KIO.Success -> {
                        val value = curKio.value
                        val nextApp = nextInstr(value)
                        if (nextApp == null) {
                            return Exit.Success(value)
                        } else {
                            curKio = nextApp
                        }
                    }

                    is KIO.Failure -> {
                        val error = curKio.error
                        unwindStack()

                        if (stack.isEmpty()) {
                            return Exit.Failure(error)
                        } else {
                            curKio = nextInstr(error)
                        }
                    }

                    is KIO.Fold<R, *, *, *, *> -> {
                        val nextApp = curKio.app
                        // There is a trick to how and why this works.
                        // Remember, that Fold implements the function interface (A) -> KIO<R, E, B>
                        // Therefore, we can cast it safely to a function and push it onto the stack.
                        // This means, a Fold behaves exactly like a FlatMap in the successful case and
                        // only when an error occurs will we unwind the stack and put the error handler
                        // on the stack.
                        stack.push(curKio as (Any?) -> KIO<R, Any?, Any?>)
                        curKio = nextApp
                    }

                    is KIO.FlatMap<*, *, *, *, *, *> -> {
                        val nextApp = curKio.app as KIO<R, Any?, Any?>
                        stack.push(curKio.f as (Any?) -> KIO<R, Any?, Any?>)
                        curKio = nextApp
                    }

                    is KIO.Comprehension<*, *, *> -> {
                        val f = curKio.f as KIO.ComprehensionScope<Any?, Any?>.() -> KIO<Any?, Any?, Any?>
                        val scope = object : KIO.ComprehensionScope<Any?, Any?> {
                            override fun <A> KIO<Any?, Any?, A>.not(): A {
                                return when (val result = RunLoop(environments.peek()).runHelp(this)) {
                                    is Exit.Success ->
                                        result.value as A

                                    is Exit.Failure ->
                                        throw InternalComprehensionException(result.error)
                                }
                            }
                        }

                        curKio = try {
                            f(scope)
                        } catch (ex: InternalComprehensionException) {
                            KIO.Failure(ex.error)
                        }
                    }

                    is KIO.Access<*, *, *> -> {
                        val f = curKio.f as (Any?) -> KIO<Any?, Any?, Any?>
                        val curEnv = environments.peek()
                        curKio = f(curEnv)
                    }

                    is KIO.EffectPartial -> {
                        val effect = curKio.effect
                        var nextIO: KIO<R, Any?, Any?>? = null

                        val value = try {
                            effect()
                        } catch (t: Throwable) {
                            if (!isFatal(t))
                                nextIO = KIO.Failure(Cause.expected(t))
                            else
                                throw t
                        }

                        curKio = nextIO ?: KIO.ok(value)
                    }

                    is KIO.EffectTotal -> {
                        val value = curKio.value()
                        val nextApp = nextInstr(value)
                        curKio = nextApp ?: KIO.ok(value)
                    }

                    is KIO.Provide<*, *, *> -> {
                        val tempEnv = curKio.env
                        val next = curKio.kio as KIO<Any?, Any?, Any?>

                        val push = KIO.effectTotal { environments.push(tempEnv) }
                        val pop = KIO.effectTotal { environments.pop() }
                        curKio = push.bracketIgnore(pop, next)
                    }

                    else -> {
                        throw RuntimeException("""
                            |The KIO<R, E, A> you have been running seems to have run into a pathological path. 
                            |
                            |The last KIO to be run was: 
                            |
                            |   $curKio
                            |
                            |Maybe a Branch has not been handled correctly.
                            |
                            |Unfortunately, you cannot do anything about this, as it is a fatal error in the implementation
                            |of KIO. Please report this at https://github.com/lambda9-gmbh/tailwind
                        """.trimMargin())
                    }
                }
            } catch (t: Throwable) {
                // Everything in here is either a bug in the interpreter
                // or a bug in the users code or a complete failure of the
                // VM. If it is a complete failure of the VM, we cannot do
                // anything so we just abort.
                if (isFatal(t))
                    throw t
                else
                    curKio = KIO.halt(Cause.panic(t))
            }
        } while (curKio != null)

        throw RuntimeException("""
            |The KIO<R, E, A> you have been running seems to have run into a pathological path. 
            |
            |Unfortunately, you cannot do anything about this, as it is a fatal error in the implementation
            |of KIO. Please report this at https://github.com/lambda9-gmbh/tailwind
        """.trimMargin())
    }

    /**
     * Check whether the given [Throwable] is fatal or not. Only [VirtualMachineError]s
     * are really fatal, e.g. [OutOfMemoryError].
     *
     * @param t any throwable
     * @return true, if it is a [VirtualMachineError] false, otherwise
     */
    private fun isFatal(t: Throwable): Boolean =
        t is VirtualMachineError

    /**
     * The [InternalComprehensionException] is only used inside this class to
     * implement a `do notation` (Haskell) like syntax, which Kotlin cannot provide
     * without this kind of hack.
     *
     * @param error the reason for the current failed computation
     */
    private class InternalComprehensionException(val error: Cause<Any?>) : Throwable()

}