package dev.entree.vchest.api

import org.bukkit.entity.Player
import org.bukkit.event.Cancellable
import org.bukkit.event.HandlerList
import org.bukkit.event.player.PlayerEvent

class ChestOpenEvent(val chestNum: Int, player: Player) : PlayerEvent(player), Cancellable {
    var cancel: Boolean = false

    override fun isCancelled(): Boolean {
        return cancel
    }

    override fun setCancelled(cancel: Boolean) {
        this.cancel = cancel
    }

    override fun getHandlers(): HandlerList {
        return getHandlerList()
    }

    companion object {
        val handlers: HandlerList = HandlerList()

        @JvmStatic
        fun getHandlerList(): HandlerList {
            return handlers
        }
    }
}
