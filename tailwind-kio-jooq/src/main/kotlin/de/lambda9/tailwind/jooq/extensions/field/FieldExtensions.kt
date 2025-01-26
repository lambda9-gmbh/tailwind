package de.lambda9.tailwind.jooq.extensions.field

import org.jooq.*
import org.jooq.impl.DSL


/**
 * A shortcut for [dereference].
 */
infix operator fun <R: UDTRecord<R>, F> Field<R>.get(field: UDTField<R, F>): Field<F> =
    dereference(field)


/**
 * Dereference a property of a [UDT].
 *
 * This function is useful, if you want to sort or filter based on a
 * [Field], that is part of a [UDT].
 *
 * # Example
 *
 * Here is a short example, on how to use this function, as well as its shortcut [get].
 *
 * ```kotlin
 * val userStreet = USERS.ADDRESS dereference Address.STREET
 * val userStreet = USERS.ADDRESS get Address.STREET
 * val userStreet = USERS.ADDRESS[Address.STREET]
 * ```
 *
 * @param field any [UDTField] of the [UDTRecord] of type [R]
 * @return a new [Field], which is part of the [UDT]
 */
infix fun <R: UDTRecord<R>, F> Field<R>.dereference(field: UDTField<R, F>): Field<F> =
    DSL.field("({0}).{1}", field.dataType, this, field.unqualifiedName)


/**
 * Returns a [Condition], that checks, whether any element of this [Field]
 * matches the condition given by [predicate].
 *
 * This function is useful, when you are working with an array and
 * want to filter a row based on any element inside the array conforming
 * to the given condition.
 *
 * # Example
 *
 * ```kotlin
 * val cond = TODOS.TAGS any { containsIgnoreCase("test") }
 * ```
 *
 * @param predicate a predicate building the condition based on a single [Field] of type [T]
 * @return a [Condition]
 */
inline infix fun <reified T> Field<Array<T>>.any(predicate: Field<T>.() -> Condition): Condition {
    val item = "item"
    val field = DSL.field(DSL.name(item), T::class.java)
    val table = DSL.unnest(this).`as`("t", item)
    val query = DSL
        .select(field)
        .from(table)
        .where(predicate(field))

    return DSL.exists(query)
}


/**
 * Returns a [Condition], that checks, whether no element of this [Field]
 * matches the condition given by [predicate].
 *
 * This function is useful, when you are working with an array and
 * want to filter a row based on no element inside the array conforming
 * to the given condition.
 *
 * # Example
 *
 * ```kotlin
 * val cond = TODOS.TAGS none { containsIgnoreCase("test") }
 * ```
 *
 * @param predicate a predicate building the condition based on a single [Field] of type [T]
 * @return a [Condition]
 */
inline infix fun <reified T> Field<Array<T>>.none(predicate: Field<T>.() -> Condition): Condition {
    val item = "item"
    val field = DSL.field(DSL.name(item), T::class.java)
    val table = DSL.unnest(this).`as`("t", item)
    val query: SelectConditionStep<Record1<T>> = DSL
        .select(field)
        .from(table)
        .where(predicate(field))

    return DSL.notExists(query)
}
