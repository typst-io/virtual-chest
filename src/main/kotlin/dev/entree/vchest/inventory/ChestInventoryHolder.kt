package dev.entree.vchest.inventory

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack

class ChestInventoryHolder(
    val onClose: (Map<Int, ItemStack>) -> Unit,
) : InventoryHolder {
    lateinit var inv: Inventory

    override fun getInventory(): Inventory {
        return inv
    }
}
