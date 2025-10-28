package dev.entree.vchest.papi

import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.entity.Player

object PlaceholderExtension {
    fun parse(p: Player?, xs: String): String = PlaceholderAPI.setPlaceholders(p, xs)
}
