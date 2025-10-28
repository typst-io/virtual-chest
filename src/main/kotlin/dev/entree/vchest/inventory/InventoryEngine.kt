package dev.entree.vchest.inventory

import dev.entree.vchest.chestPlugin
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

object InventoryEngine : Listener {
    @EventHandler
    fun onClose(e: InventoryCloseEvent) {
        val chestHolder = e.inventory.holder as? ChestInventoryHolder
        if (chestHolder != null) {
            val itemMap = e.inventory.contents.withIndex()
                .flatMap { pair ->
                    val item = pair.value
                    if (item != null && item.type != Material.AIR) {
                        listOf(pair.index to item)
                    } else emptyList()
                }
                .toMap()
            chestHolder.onClose(itemMap)
        }
    }

    fun register(plugin: Plugin) {
        Bukkit.getPluginManager().registerEvents(InventoryEngine, plugin)
    }

    @Suppress("DEPRECATION")
    fun createChest(
        title: String,
        items: Map<Int, ItemStack>,
        onClose: (Map<Int, ItemStack>) -> Unit,
    ): Inventory {
        val inv = Bukkit.createInventory(
            ChestInventoryHolder(onClose),
            chestPlugin.pluginConfig.chestSizeRow.coerceIn(1, 6) * 9,
            title
        )
        for ((slot, item) in items) {
            inv.setItem(slot, item)
        }
        return inv
    }
}
