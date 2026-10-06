package dev.entree.vchest

import dev.entree.vchest.cmd.ChestCommand
import dev.entree.vchest.config.PluginConfig
import dev.entree.vchest.inventory.ChestInventoryHolder
import dev.entree.vchest.support.ConfigReloadScope
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.*
import java.nio.file.Path
import java.sql.SQLException
import java.util.concurrent.CompletableFuture

class ConfigReloadRegressionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `switching from SQLite to MySQL connects using the new configuration`() {
        ConfigReloadScope(directory).use { reload ->
            reload.reload(PluginConfig(dbProtocol = "mysql", dbPassword = "test-password"))
            assertEquals("mysql", reload.scope.plugin.pluginConfig.dbProtocol)
            assertEquals(2, reload.configurations.size)
            assertTrue(reload.configurations.last().jdbcUrl.startsWith("jdbc:mysql:"),
                "The replacement repository must use newConfig rather than the old SQLite settings")
        }
    }

    @Test
    fun `a datasource switch preserves changes from a chest that is already open`() {
        ConfigReloadScope(directory).use { reload ->
            val scope = reload.scope
            val owner = scope.player()
            val ownerId = owner.uniqueId
            val oldRepository = spy(scope.plugin.repository)
            scope.plugin.repository = oldRepository
            val oldPool = reload.pools.single()
            var saved: Map<Int, ByteArray>? = null
            doReturn(CompletableFuture.completedFuture(emptyList<dev.entree.vchest.dbms.SlotDAO>()))
                .`when`(oldRepository).fetchChest(ownerId, 1)
            doAnswer {
                if (oldPool in reload.closedPools) CompletableFuture.failedFuture<IntArray>(SQLException("old datasource closed"))
                else {
                    saved = it.getArgument(2)
                    CompletableFuture.completedFuture(intArrayOf(1))
                }
            }.`when`(oldRepository).upsertChest(eq(ownerId) ?: ownerId, eq(1), anyMap())

            lateinit var holder: ChestInventoryHolder
            scope.bukkit.`when`<Inventory> {
                org.bukkit.Bukkit.createInventory(any(InventoryHolder::class.java), anyInt(), anyString())
            }.thenAnswer {
                holder = it.getArgument(0)
                mock(Inventory::class.java)
            }
            assertTrue(ChestCommand.openChest(OpenChestContext(owner, 1)).join())
            val item = mock(ItemStack::class.java)
            `when`(item.serializeAsBytes()).thenReturn(byteArrayOf(42))
            reload.reload(PluginConfig(dbProtocol = "mysql", dbPassword = "test-password"))
            holder.onClose(mapOf(0 to item))
            assertNotNull(saved, "An open chest must stay saveable until its old repository is drained")
            assertArrayEquals(byteArrayOf(42), saved!![0])
        }
    }

    @Test
    fun `changing only the chest title leaves the datasource usable`() {
        ConfigReloadScope(directory).use { reload ->
            val before = reload.scope.plugin.repository
            reload.reload(reload.scope.plugin.pluginConfig.copy(chestTitle = "Updated title"))
            assertSame(before, reload.scope.plugin.repository)
            assertTrue(reload.closedPools.isEmpty())
            assertEquals("Updated title", reload.scope.plugin.pluginConfig.chestTitle)
        }
    }
}
