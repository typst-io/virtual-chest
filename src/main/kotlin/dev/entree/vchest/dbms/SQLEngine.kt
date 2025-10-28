package dev.entree.vchest.dbms

import dev.entree.vchest.chestPlugin
import dev.entree.vchest.thenAcceptSync
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.Plugin

object SQLEngine : Listener {
    @EventHandler
    fun onJoin(e: PlayerJoinEvent) {
        chestPlugin.repository.getOrCreatePlayer(e.player.uniqueId).thenAcceptSync {
            // ignore
        }
    }

    fun register(plugin: Plugin) {
        Bukkit.getPluginManager().registerEvents(SQLEngine, plugin)
    }
}