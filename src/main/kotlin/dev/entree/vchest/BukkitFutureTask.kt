package dev.entree.vchest

import java.util.concurrent.CompletableFuture

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
