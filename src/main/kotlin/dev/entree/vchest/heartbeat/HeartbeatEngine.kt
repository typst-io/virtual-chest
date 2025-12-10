package dev.entree.vchest.heartbeat

import dev.entree.vchest.ChestPlugin
import dev.entree.vchest.chestPlugin
import dev.entree.vchest.dbms.ChestKey
import org.bukkit.event.Listener
import org.bukkit.scheduler.BukkitRunnable
import java.time.Duration
import java.time.LocalDateTime

object HeartbeatEngine : Listener {
    fun register(plugin: ChestPlugin) {
        object : BukkitRunnable() {
            override fun run() {
                val now = LocalDateTime.now()
                val duration = Duration.between(chestPlugin.lastTick, now)
                if (duration < Duration.ofSeconds(1)) return
                chestPlugin.lastTick = now
                val updates = mutableSetOf<ChestKey>()
                for ((playerId, viewCtx) in chestPlugin.openingChests) {
                    updates += ChestKey(viewCtx.num, playerId)
                }
                chestPlugin.repository.renewChestExpiration(updates)
            }
        }.runTaskTimer(plugin, 1L, 1L)
    }
}