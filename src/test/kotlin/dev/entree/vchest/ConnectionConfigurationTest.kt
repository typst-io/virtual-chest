package dev.entree.vchest

import com.mysql.cj.conf.ConnectionUrl
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mockConstruction
import java.nio.file.Path
import java.util.*
import java.util.logging.Logger

class ConnectionConfigurationTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `SQLite stores its database inside the configured data directory`() {
        val context = JDBCContext.ofSqlite(Logger.getAnonymousLogger(), "chest", directory.toFile())
        JDBCUtils.getDataSource(context).use { source ->
            val actual = source.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery("PRAGMA database_list").use { rows ->
                        assertTrue(rows.next())
                        Path.of(rows.getString("file")).toAbsolutePath().normalize()
                    }
                }
            }
            assertEquals(directory.resolve("chest.db").toAbsolutePath().normalize(), actual,
                "Encoding path separators must not redirect SQLite to a relative filename")
        }
    }

    @Test
    fun `MySQL connections require encrypted transport and server identity verification`() {
        val properties = mysqlConnectionProperties()
        assertAll(
            { assertNotEquals("false", properties.getProperty("useSSL"), "TLS must not be explicitly disabled") },
            { assertEquals("VERIFY_IDENTITY", properties.getProperty("sslMode"), "The server certificate and hostname must be verified") }
        )
    }

    @Test
    fun `MySQL uses affected rows so an unchanged lock update is not a success`() {
        assertEquals("true", mysqlConnectionProperties().getProperty("useAffectedRows"))
    }

    private fun mysqlConnectionProperties(): Properties {
        lateinit var configuration: HikariConfig
        mockConstruction(HikariDataSource::class.java) { _, context ->
            configuration = context.arguments().single() as HikariConfig
        }.use {
            JDBCUtils.getDataSource(JDBCContext.ofMySQL(
                Logger.getAnonymousLogger(), "localhost", "3306", "test", "test-password", "mc_virtual_chest"
            )).close()
            return ConnectionUrl.getConnectionUrlInstance(configuration.jdbcUrl, null)
                .connectionArgumentsAsProperties.apply { putAll(configuration.dataSourceProperties) }
        }
    }
}
