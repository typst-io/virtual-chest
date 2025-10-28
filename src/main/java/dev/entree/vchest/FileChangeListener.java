package dev.entree.vchest;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.*;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.logging.Level;

import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

public class FileChangeListener {
    public static Optional<Closeable> register(String filename, Consumer<YamlConfiguration> listener, Plugin plugin) {
        return registerPrime(filename, txt -> listener.accept(YamlConfiguration.loadConfiguration(new StringReader(txt))), plugin);
    }

    public static Optional<Closeable> registerPrime(String filename, Consumer<String> listener, Plugin plugin) {
        try {
            WatchService watcher = FileSystems.getDefault().newWatchService();
            Path dir = plugin.getDataFolder().toPath();
            dir.register(watcher, ENTRY_MODIFY);
            new BukkitRunnable() {
                @Override
                public void run() {
                    WatchKey key;
                    try {
                        while (plugin.isEnabled() && (key = watcher.poll()) != null) {
                            for (WatchEvent<?> event : key.pollEvents()) {
                                WatchEvent.Kind<?> kind = event.kind();
                                if (kind == OVERFLOW) {
                                    continue;
                                }
                                Path path = event.context() instanceof Path ? ((Path) event.context()) : null;
                                if (path == null || !path.getFileName().toString().equals(filename)) {
                                    continue;
                                }
                                Path child = dir.resolve(path);
                                if (child.toFile().length() > 0) {
                                    try {
                                        String text = String.join("\n", Files.readAllLines(child));
                                        Bukkit.getScheduler().runTask(plugin, () -> listener.accept(text));
                                    } catch (Exception ex) {
                                        log(plugin, ex);
                                    }
                                }
                            }
                            boolean valid = key.reset();
                            if (!valid) {
                                break;
                            }
                        }
                    } catch (ClosedWatchServiceException ex) {
                        cancel();
                    }
                }
            }.runTaskTimer(plugin, 0L, 0L);
            return Optional.of(watcher);
        } catch (IOException ex) {
            log(plugin, ex);
            return Optional.empty();
        }
    }

    private static void log(Plugin plugin, Throwable ex) {
        plugin.getLogger().log(Level.WARNING, ex, () -> "Error while listening the folder: " + plugin.getDataFolder().getAbsolutePath());
    }
}
