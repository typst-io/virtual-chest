package dev.entree.vchest

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier


fun Plugin.runTask(delay: Long = 0, runnable: () -> Unit): BukkitTask {
    return object : BukkitRunnable() {
        override fun run() = runnable()
    }.runTaskLater(this, delay)
}

fun Plugin.runTaskAsync(delay: Long = 0, runnable: () -> Unit): BukkitTask {
    return object : BukkitRunnable() {
        override fun run() = runnable()
    }.runTaskLaterAsynchronously(this, delay)
}

fun <A> Plugin.futureTaskAsync(delay: Long = 0, supplier: Supplier<A>): CompletableFuture<A> {
    return if (isEnabled && Bukkit.isPrimaryThread()) {
        val future = CompletableFuture<A>()
        val taskId = runTaskAsync(delay) {
            try {
                future.complete(supplier.get())
            } catch (ex: Exception) {
                future.completeExceptionally(ex)
            }
        }.taskId
        future
    } else {
        val future = try {
            CompletableFuture.completedFuture(supplier.get())
        } catch (ex: Exception) {
            CompletableFuture.failedFuture(ex)
        }
        future
    }
}

fun Player.giveItemOrDrop(item: ItemStack) {
    inventory.addItem(item).forEach { (_, v) ->
        world.dropItem(eyeLocation, v)
    }
}
