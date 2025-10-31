package dev.entree.vchest

import java.io.File
import java.util.logging.Logger

data class JDBCContext(
    var logger: Logger,
    var host: String,
    var port: String,
    var username: String,
    var password: String,
    var dbName: String,
    var protocol: String,
    var dir: File?,
    var className: String,
    var classLoader: ClassLoader?,
) {
    companion object {
        fun ofMariaDB(
            logger: Logger,
            host: String,
            port: String,
            username: String,
            password: String,
            dbName: String,
        ): JDBCContext {
            return JDBCContext(
                logger,
                host,
                port,
                username,
                password,
                dbName,
                "mariadb",
                null,
                "org.mariadb.jdbc.Driver",
                null
            )
        }

        fun ofMySQL(
            logger: Logger,
            host: String,
            port: String,
            username: String,
            password: String,
            dbName: String,
        ): JDBCContext {
            return JDBCContext(
                logger,
                host,
                port,
                username,
                password,
                dbName,
                "mysql",
                null,
                "com.mysql.cj.jdbc.Driver",
                null
            )
        }

        fun ofSqlite(logger: Logger, dbName: String, dir: File): JDBCContext {
            return JDBCContext(logger, "", "", "", "", dbName, "sqlite", dir, "", null)
        }
    }
}