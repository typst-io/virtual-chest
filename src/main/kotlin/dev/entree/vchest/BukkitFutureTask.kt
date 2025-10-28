package dev.entree.vchest

import org.bukkit.plugin.Plugin
import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

fun <A> Plugin.futureTask(delay: Long = 0, supplier: Supplier<A>): CompletableFuture<A> {
    return if (isEnabled) {
        val future = CompletableFuture<A>()
        runTaskAsync(delay) {
            try {
                future.complete(supplier.get())
            } catch (ex: Exception) {
                future.completeExceptionally(ex)
            }
        }
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

fun <A, B> CompletableFuture<A>.map(f: (A) -> B): CompletableFuture<B> {
    if (chestPlugin.isEnabled) {
        return thenApplyAsync(f, chestPlugin.syncExecutor)
    } else {
        return thenApply { f(it) }
    }
}

fun <A, B> CompletableFuture<A>.flatMap(f: (A) -> CompletableFuture<B>): CompletableFuture<B> {
    if (chestPlugin.isEnabled) {
        return thenComposeAsync(f, chestPlugin.syncExecutor)
    } else {
        return thenCompose { f(it) }
    }
}
