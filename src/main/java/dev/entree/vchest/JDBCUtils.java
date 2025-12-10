package dev.entree.vchest;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

public class JDBCUtils {
    public static HikariDataSource getDataSource(JDBCContext ctx) {
        String protocol = ctx.getProtocol();
        String host = ctx.getHost();
        String port = ctx.getPort();
        String username = ctx.getUsername();
        String password = ctx.getPassword();
        File dbDir = ctx.getDir();
        String dbName = ctx.getDbName();
        // hikari
        HikariConfig hikariConfig = new HikariConfig();
        Stream<Object> args = dbDir != null
                ? Stream.of(protocol, new File(dbDir, dbName + ".db").getAbsolutePath())
                : Stream.of(protocol, host, port, dbName, username, password);
        Object[] escapeArgs = args
                .map(xs -> URLEncoder.encode(xs.toString(), StandardCharsets.UTF_8))
                .toArray();
        String url = dbDir != null
                ? String.format("jdbc:%s:%s", escapeArgs)
                : String.format("jdbc:%s://%s:%s/%s?createDatabaseIfNotExist=true&user=%s&password=%s&useSSL=false&allowPublicKeyRetrieval=true&useAffectedRows=true", escapeArgs);
        hikariConfig.setJdbcUrl(url);
        return new HikariDataSource(hikariConfig);
    }

    public static void initDatabase(HikariDataSource dataSource, @Nullable ClassLoader loader, String protocol) {
        ClassLoader prevLoader = Thread.currentThread().getContextClassLoader();
        if (loader != null) {
            Thread.currentThread().setContextClassLoader(loader);
        }
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration/" + protocol)
                .baselineOnMigrate(true)             // 기존 DB에 스키마 히스토리 없을 때 베이스라인 허용
                .outOfOrder(false)                   // 버전 역전 허용 여부
                .cleanDisabled(true)                 // 운영에서는 clean 금지 권장
                .validateMigrationNaming(true)
                .load()
                .migrate();
        if (loader != null) {
            Thread.currentThread().setContextClassLoader(prevLoader);
        }
    }
}
