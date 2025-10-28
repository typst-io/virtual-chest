package dev.entree.vchest;

import lombok.With;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.logging.Logger;

@With
public record JDBCContext(Logger logger, String host, String port, String username, String password, String dbName,
                          String protocol, @Nullable File dir, String className, @Nullable ClassLoader classLoader) {
    public JDBCContext(Logger logger, String host, String port, String username, String password, String dbName, String protocol, @Nullable File dir, String className, ClassLoader classLoader) {
        this.logger = logger;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.dbName = dbName;
        this.protocol = protocol;
        this.dir = dir;
        this.className = className;
        this.classLoader = classLoader;
    }

    public static JDBCContext ofMariaDB(Logger logger, String host, String port, String username, String password, String dbName) {
        return new JDBCContext(logger, host, port, username, password, dbName, "mariadb", null, "org.mariadb.jdbc.Driver", null);
    }

    public static JDBCContext ofMySQL(Logger logger, String host, String port, String username, String password, String dbName) {
        return new JDBCContext(logger, host, port, username, password, dbName, "mysql", null, "com.mysql.cj.jdbc.Driver", null);
    }

    public static JDBCContext ofSqlite(Logger logger, String dbName, File dir) {
        return new JDBCContext(logger, "", "", "", "", dbName, "sqlite", dir, "", null);
    }
}
