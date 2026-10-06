package dev.entree.vchest.support

import org.testcontainers.mysql.MySQLContainer
import java.io.Closeable
import java.net.ServerSocket
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.util.concurrent.TimeUnit

/** Never accepts a JDBC URL for an existing database. Each backend creates its own server. */
class IsolatedMySQL private constructor(
    val jdbcUrl: String,
    private val username: String,
    private val password: String,
    private val cleanup: () -> Unit,
) : Closeable {
    fun connection(): Connection = DriverManager.getConnection(jdbcUrl, username, password)

    override fun close() = cleanup()

    companion object {
        fun start(directory: Path): IsolatedMySQL {
            val executable = System.getenv("MYSQL_TEST_MYSQLD")?.takeIf(String::isNotBlank)
            if (executable == null) {
                val container = MySQLContainer("mysql:8.4.8")
                    .withDatabaseName("mc_virtual_chest")
                    .withUsername("root")
                    .withPassword("test-password")
                    .withUrlParam("useAffectedRows", "true")
                container.start()
                return IsolatedMySQL(container.jdbcUrl, container.username, container.password, container::close)
            }

            val binary = Path.of(executable).toAbsolutePath()
            require(Files.isRegularFile(binary)) { "MYSQL_TEST_MYSQLD must point to a mysqld executable: $binary" }
            val data = Files.createDirectory(directory.resolve("data"))
            val log = directory.resolve("mysqld.log").toFile()
            val baseArguments = listOf(
                binary.toString(), "--no-defaults", "--basedir=${binary.parent.parent}",
                "--datadir=$data", "--innodb-buffer-pool-size=64M", "--console"
            )
            val initializer = ProcessBuilder(baseArguments + "--initialize-insecure")
                .redirectErrorStream(true).redirectOutput(log).start()
            if (!initializer.waitFor(120, TimeUnit.SECONDS)) {
                initializer.destroyForcibly().waitFor()
                error("MySQL initialization timed out; see $log")
            }
            check(initializer.exitValue() == 0) { "MySQL initialization failed: ${log.readText()}" }

            val port = ServerSocket(0).use { it.localPort }
            val process = ProcessBuilder(baseArguments + listOf(
                "--port=$port", "--bind-address=127.0.0.1", "--mysqlx=0",
                "--skip-log-bin", "--max-connections=20"
            )).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log)).start()
            val url = "jdbc:mysql://127.0.0.1:$port/mc_virtual_chest" +
                "?createDatabaseIfNotExist=true&useAffectedRows=true&useSSL=false&allowPublicKeyRetrieval=true" +
                "&connectTimeout=3000&socketTimeout=3000"
            val server = IsolatedMySQL(url, "root", "") {
                runCatching { DriverManager.getConnection(url, "root", "").use { it.createStatement().execute("SHUTDOWN") } }
                if (!process.waitFor(10, TimeUnit.SECONDS)) {
                    process.destroyForcibly().waitFor(10, TimeUnit.SECONDS)
                }
            }
            val deadline = System.nanoTime() + Duration.ofSeconds(60).toNanos()
            while (process.isAlive && System.nanoTime() < deadline) {
                if (runCatching { server.connection().use { it.isValid(1) } }.getOrDefault(false)) return server
                Thread.sleep(100)
            }
            server.close()
            error("Isolated MySQL did not start: ${log.readText()}")
        }
    }
}
