package dev.entree.vchest

import org.bukkit.entity.Player

data class OpenChestContext(
    val viewer: Player,
    val num: Int,
    val target: Player = viewer,
    val title: String = "",
    val onClose: ((Result<IntArray>) -> Unit)? = null,
) {
    val isOwnerPlayer: Boolean get() = viewer.uniqueId == target.uniqueId
}
