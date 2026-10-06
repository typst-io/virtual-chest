package dev.entree.vchest

import dev.entree.vchest.support.PluginTestScope
import org.bukkit.Material
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryView
import org.bukkit.inventory.ItemStack
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import java.util.concurrent.CompletableFuture

class ShutdownRegressionTest {
    @Test
    fun `shutdown saves a chest viewed by its owner`() {
        PluginTestScope().use { scope ->
            val owner = scope.player()
            val ownerId = owner.uniqueId
            val inventory = mock(Inventory::class.java)
            `when`(inventory.contents).thenReturn(arrayOf(ItemStack(Material.DIAMOND)))
            val view = mock(InventoryView::class.java)
            `when`(view.topInventory).thenReturn(inventory)
            `when`(owner.openInventory).thenReturn(view)
            scope.openingChests[ownerId] = ChestViewContext(ownerId, 1, ownerId)
            var saved: Map<Int, ByteArray>? = null
            doAnswer {
                saved = it.getArgument(2)
                CompletableFuture.completedFuture(intArrayOf(1))
            }.`when`(scope.repository).upsertChest(eq(ownerId) ?: ownerId, eq(1), anyMap())
            mockConstruction(ItemStack::class.java) { item, _ ->
                `when`(item.serializeAsBytes()).thenReturn(byteArrayOf(42))
            }.use { scope.plugin.onDisable() }
            assertNotNull(saved)
            assertArrayEquals(byteArrayOf(42), saved!![0])
        }
    }

    @Test
    fun `shutdown saves the viewers chest inventory under its owner UUID`() {
        PluginTestScope().use { scope ->
            val owner = scope.player()
            val viewer = scope.player()
            val ownerId = owner.uniqueId
            val viewerId = viewer.uniqueId
            val ownerInventory = mock(Inventory::class.java)
            val viewerInventory = mock(Inventory::class.java)
            `when`(ownerInventory.contents).thenReturn(emptyArray())
            `when`(viewerInventory.contents).thenReturn(arrayOf(ItemStack(Material.DIAMOND)))
            val ownerView = mock(InventoryView::class.java)
            val viewerView = mock(InventoryView::class.java)
            `when`(ownerView.topInventory).thenReturn(ownerInventory)
            `when`(viewerView.topInventory).thenReturn(viewerInventory)
            `when`(owner.openInventory).thenReturn(ownerView)
            `when`(viewer.openInventory).thenReturn(viewerView)
            scope.openingChests[ownerId] = ChestViewContext(ownerId, 1, viewerId)
            var saved: Map<Int, ByteArray>? = null
            doAnswer {
                saved = it.getArgument(2)
                CompletableFuture.completedFuture(intArrayOf(1))
            }.`when`(scope.repository).upsertChest(eq(ownerId) ?: ownerId, eq(1), anyMap())
            // Paper's item serialization requires a server implementation; isolate that boundary.
            val marker = byteArrayOf(42)
            mockConstruction(ItemStack::class.java) { item, _ ->
                `when`(item.serializeAsBytes()).thenReturn(marker)
            }.use {
                scope.plugin.onDisable()
            }
            assertNotNull(saved, "The owner's chest must be persisted")
            assertEquals(setOf(0), saved!!.keys, "The admin's diamond must not be replaced by the owner's empty inventory")
            assertArrayEquals(marker, saved!![0])
        }
    }
}
