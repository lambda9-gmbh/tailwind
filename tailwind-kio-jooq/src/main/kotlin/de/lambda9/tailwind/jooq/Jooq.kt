package de.lambda9.tailwind.jooq

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import de.lambda9.tailwind.core.KIO
import de.lambda9.tailwind.core.extensions.kio.refineOrDie
import de.lambda9.tailwind.jooq.config.DatabaseConfig
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.exception.DataAccessException
import org.jooq.impl.DSL
import org.jooq.impl.DefaultConfiguration
import org.jooq.impl.DefaultExecuteListenerProvider
import java.io.PrintWriter
import java.io.Serializable
import java.util.*


/**
 * A [JIO] is a [KIO], which requires an environment of [Jooq], may
 * fail with a [DataAccessException] and produces a value of type [A].
 */
typealias JIO<A> = KIO<Jooq<Any?>, DataAccessException, A>

/**
 * An environment, which contains a [DSLContext] in order to
 * allow for a generic implementation of [transact].
 *
 * @param dsl a [DSLContext] from JOOQ.
 * @param env a user defined additional environment.
 */
data class Jooq<out Env>(
    // This should actually be internal, which would mean a
    // Jooq.query is the only way to actually perform a query.
    // However, due to backward compatibility it isn't.
    val dsl: DSLContext,
    val env: Env,
): Serializable {

    /**
     *
     * @param url
     * @param user
     * @param password
     * @param schema
     * @param dialect
     * @param driver
     * @param queryPrinter
     */
    data class Config(
        var url: String = "jdbc:postgresql://localhost/db",
        var user: String = "lambda",
        var password: String = "sql",
        var schema: String? = null,
        var dialect: SQLDialect = SQLDialect.POSTGRES,
        var driver: String? =
            when (dialect) {
                SQLDialect.DEFAULT -> null
                SQLDialect.DERBY -> null
                SQLDialect.FIREBIRD -> null
                SQLDialect.H2 -> null
                SQLDialect.HSQLDB -> null
                SQLDialect.MARIADB -> "org.mariadb.jdbc.Driver"
                SQLDialect.MYSQL -> "com.mysql.jdbc.Driver"
                SQLDialect.POSTGRES -> "org.postgresql.Driver"
                SQLDialect.SQLITE -> null
                SQLDialect.YUGABYTEDB -> null
                else -> null
        },
        var queryPrinter: Boolean = true,
    ): Serializable

    companion object {

        /**
         * Returns a new [JIO], which will allow to query the database.
         *
         * This function is intended to be used primarily for querying the
         * database and nothing else. The reason for this is, that it is
         * possible in future versions of this library to shift just the
         * querying onto a blocking thread pool.
         *
         * **Note**: For this reason, if you are using this function inside
         * a comprehension, you *should never* run another monadic action
         * inside a Jooq.query.
         *
         * # Example
         *
         * ```kotlin
         * fun find(id: Long) = Jooq.query {
         *     selectFrom(...)
         * }
         * ```
         *
         * @param runQuery a function actually working with the [DSLContext]
         * @return a new [KIO]
         */
        fun <A> query(runQuery: DSLContext.() -> A): JIO<A> =
            queryWithEnvironment { runQuery() }

        /**
         * Returns a new [KIO], which will allow to query the database while having
         * access to some form of environment of type [R].
         *
         * This function is intended to be used primarily for querying the
         * database and nothing else. The reason for this is, that it is
         * possible in future versions of this library to shift just the
         * querying onto a blocking thread pool.
         *
         * **Note**: For this reason, if you are using this function inside
         * a comprehension, you *should never* run another monadic action
         * inside a Jooq.query.
         *
         * @param runQuery A function running a query and working with the [DSLContext]
         * @return a new [KIO]
         */
        fun <R, A> queryWithEnvironment(runQuery: DSLContext.(R) -> A): KIO<Jooq<R>, DataAccessException, A> =
            KIO.accessM { KIO { runQuery(it.dsl, it.env) }.refineOrDie(DataAccessException::class) }

        /**
         * Create a new [Jooq] environment from the given [config] and [env].
         *
         * @param init a database configuration
         * @param env the rest of the environment
         * @return a new Jooq
         */
        fun <R> create(env: R, init: Config.() -> Unit): Pair<Jooq<R>, HikariDataSource> {
            val config = Config().apply(init)
            val props = Properties().apply {
                put("dataSource.logWriter", PrintWriter(System.out))
            }

            val ds = HikariDataSource(HikariConfig(props).apply {
                jdbcUrl = config.url
                driverClassName = config.driver
                password = config.password
                username = config.user
                schema = config.schema
            })

            val dslConfiguration = DefaultConfiguration()
                .set(ds)
                .set(config.dialect)
                .set(DefaultExecuteListenerProvider(DeleteOrUpdateWithoutWhereListener()))
                .apply {
                    if (config.queryPrinter) {
                        set(JooqQueryPrinter())
                    }
                }

            return Jooq(
                dsl = DSL.using(dslConfiguration),
                env = env,
            ) to ds
        }

    }

}
