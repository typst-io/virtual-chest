package dev.entree.vchest

import dev.entree.vchest.cmd.ChestCommand
import dev.entree.vchest.support.PluginTestScope
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.sql.SQLException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException

class ChestSaveRegressionTest {
    @Test
    fun `a serialization failure does not commit a partial chest`() {
        PluginTestScope().use { scope ->
            val player = scope.player()
            val playerId = player.uniqueId
            val good = mock(ItemStack::class.java)
            val bad = mock(ItemStack::class.java)
            `when`(good.serializeAsBytes()).thenReturn(byteArrayOf(42))
            `when`(bad.serializeAsBytes()).thenThrow(IllegalStateException("simulated invalid item metadata"))
            var persisted: Map<Int, ByteArray>? = null
            doAnswer {
                persisted = it.getArgument(2)
                CompletableFuture.completedFuture(intArrayOf(1))
            }.`when`(scope.repository).upsertChest(eq(playerId) ?: playerId, eq(1), anyMap())

            val result = ChestCommand.saveChest(player, 1, mapOf(0 to good, 1 to bad), scope.repository)
            assertAll(
                { assertTrue(result.isCompletedExceptionally, "Serialization failure must fail the whole save") },
                { assertNull(persisted, "The original chest must not be replaced by a partial item list") }
            )
        }
    }

    @Test
    fun `a failed save invokes the close callback with the original failure`() {
        PluginTestScope().use {
            val failure = SQLException("simulated connection outage")
            var notification: Result<IntArray>? = null
            val future = CompletableFuture.failedFuture<IntArray>(failure).thenAcceptSyncPrime { notification = it }
            assertThrows(CompletionException::class.java) { future.join() }
            assertNotNull(notification, "The caller must be notified when persistence fails")
            assertSame(failure, notification!!.exceptionOrNull())
        }
    }

    @Test
    fun `a successful save serializes every slot and notifies its callback once`() {
        PluginTestScope().use { scope ->
            val player = scope.player()
            val playerId = player.uniqueId
            val item = mock(ItemStack::class.java)
            `when`(item.serializeAsBytes()).thenReturn(byteArrayOf(42))
            var persisted: Map<Int, ByteArray>? = null
            doAnswer {
                persisted = it.getArgument(2)
                CompletableFuture.completedFuture(intArrayOf(1))
            }.`when`(scope.repository).upsertChest(eq(playerId) ?: playerId, eq(1), anyMap())
            var notifications = 0
            val result = ChestCommand.saveChest(player, 1, mapOf(3 to item), scope.repository)
                .thenAcceptSyncPrime { outcome ->
                    notifications++
                    assertTrue(outcome.isSuccess)
                }.join()
            assertArrayEquals(intArrayOf(1), result)
            assertEquals(setOf(3), persisted!!.keys)
            assertArrayEquals(byteArrayOf(42), persisted!![3])
            assertEquals(1, notifications)
        }
    }
}
