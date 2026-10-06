package dev.entree.vchest

import com.zaxxer.hikari.HikariDataSource
import dev.entree.vchest.cmd.ChestCommand
import dev.entree.vchest.dbms.DatabaseSnapshotDAO
import dev.entree.vchest.support.PluginTestScope
import org.bukkit.command.CommandSender
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.sql.SQLException
import java.util.concurrent.CompletableFuture

class MigrationCommandRegressionTest {
    @Test
    fun `failed migration writes do not report migration done`() {
        PluginTestScope().use { scope ->
            `when`(scope.repository.getWholeSnapshot()).thenReturn(CompletableFuture.completedFuture(
                DatabaseSnapshotDAO(emptyList(), emptyList(), emptyList())
            ))
            val sender = mock(CommandSender::class.java)
            val messages = mutableListOf<String>()
            doAnswer { messages += it.getArgument<String>(0); null }.`when`(sender).sendMessage(anyString())
            var attemptedWrite = false
            mockStatic(JDBCUtils::class.java, CALLS_REAL_METHODS).use { jdbc ->
                jdbc.`when`<Unit> { JDBCUtils.initDatabase(any(HikariDataSource::class.java), any(), anyString()) }
                    .thenAnswer { null }
                mockConstruction(HikariDataSource::class.java) { pool, _ ->
                    `when`(pool.connection).thenAnswer {
                        attemptedWrite = true
                        throw SQLException("simulated migration write failure")
                    }
                }.use {
                    ChestCommand.execute(sender, ChestCommand.DataMigration("mysql"))
                }
            }
            assertTrue(attemptedWrite, "The destination write must actually be attempted")
            assertFalse(messages.any { it.contains("migration done", ignoreCase = true) },
                "A failed destination write must never be advertised as a completed migration")
        }
    }
}
