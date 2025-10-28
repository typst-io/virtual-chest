package dev.entree.vchest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.server.ServerCommandEvent;

public class BukkitCommandUtils {
    public static ServerCommandEvent executeConsole(String cmd) {
        String unslashedCmd = cmd.startsWith("/") ? cmd.substring(1) : cmd;

        ServerCommandEvent event = new ServerCommandEvent(Bukkit.getConsoleSender(),  unslashedCmd);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), unslashedCmd);
        }
        return event;
    }

    public static PlayerCommandPreprocessEvent executePlayer(Player player, String cmd) {
        String unslashedCmd = cmd.startsWith("/") ? cmd.substring(1) : cmd;

        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, cmd);
        Bukkit.getPluginManager().callEvent(event);
        if (!event.isCancelled()) {
            player.performCommand(unslashedCmd);
        }
        return event;
    }
}
