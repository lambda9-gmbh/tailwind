package de.lambda9.tailwind.jooq

import io.github.oshai.kotlinlogging.KotlinLogging
import org.jooq.ExecuteContext
import org.jooq.ExecuteListener
import org.jooq.conf.Settings
import org.jooq.impl.DSL

/**
 *
 *
 * @param predicate
 */
class JooqQueryPrinter(
    private val predicate: (String) -> Boolean = { true },
): ExecuteListener {

    private var start: Long = 0

    override fun start(ctx: ExecuteContext) {
        super.executeStart(ctx)
        start = System.currentTimeMillis()
    }

    override fun end(ctx: ExecuteContext) {
        super.executeEnd(ctx)

        val diff = System.currentTimeMillis() - start

        if (logger.isDebugEnabled()) {
            val create = DSL.using(ctx.dialect(), Settings())
            if (ctx.query() != null) {
                val query = create.renderInlined(ctx.query())
                if (predicate(query)) {
                    logger.debug { "${diff}ms, $query" }
                }
            }
        }
    }

    companion object {
        private val logger = KotlinLogging.logger {  }
    }

}
