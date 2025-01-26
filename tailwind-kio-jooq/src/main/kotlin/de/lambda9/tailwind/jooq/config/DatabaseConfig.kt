package de.lambda9.tailwind.jooq.config

import org.jooq.SQLDialect
import java.io.Serializable

/**
 * A [DatabaseConfig] is a serializable configuration for connecting with a database.
 *
 * @param dialect
 * @param url
 * @param user
 * @param password
 * @param schema
 * @param driver
 */
data class DatabaseConfig(
    val url: String,
    val user: String,
    val password: String,
    val schema: String? = null,
    val dialect: SQLDialect = SQLDialect.POSTGRES,
    val driver: String? = when (dialect) {
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
    }
): Serializable