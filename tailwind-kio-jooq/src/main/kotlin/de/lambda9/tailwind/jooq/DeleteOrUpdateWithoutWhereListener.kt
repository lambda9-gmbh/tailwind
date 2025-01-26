package de.lambda9.tailwind.jooq

import org.jooq.ExecuteContext
import org.jooq.ExecuteListener

/**
 * Represent an Exception, which will be thrown when
 * an update statement has been constructed, which has
 * no WHERE clause.
 */
class DeleteOrUpdateWithoutWhereException(message: String) : RuntimeException(message)

/**
 * Implements a Listener, for reacting to UPDATE and
 * DELETE statements without a WHERE clause.
 */
class DeleteOrUpdateWithoutWhereListener : ExecuteListener {

    override fun renderEnd(ctx: ExecuteContext) {
        if (ctx.sql()?.matches(Regex("^(?i:(UPDATE|DELETE)(?!.* WHERE ).*)$")) == true) {
            throw DeleteOrUpdateWithoutWhereException("""
                |You tried to use an UPDATE or DELETE query without a WHERE
                |condition. Please reconsider, whether that is what you actually want.
                |
                |If you REALLY want or MUST do such a query, please use 
                |
                |    dsl.update(...).where(DSL.trueCondition())
                |
                |
            """.trimMargin())
        }
    }
}
