package dev.entree.vchest.heartbeat

import dev.entree.vchest.ChestPlugin
import dev.entree.vchest.chestPlugin
import org.bukkit.event.Listener
import org.bukkit.scheduler.BukkitRunnable

object HeartbeatEngine : Listener {
    fun register(plugin: ChestPlugin) {
        object : BukkitRunnable() {
            override fun run() {
                for ((playerId, viewCtx) in chestPlugin.openingChests) {
                    chestPlugin.repository.renewChestExpiration(playerId, viewCtx.num)
                }
            }
        }.runTaskTimer(plugin, 20L, 20L)
    }
}