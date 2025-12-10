package dev.entree.vchest.dbms

import java.io.Closeable
import java.util.*
import java.util.concurrent.CompletableFuture

interface ChestRepository : Closeable {
    fun upsertChest(playerUid: UUID, num: Int, items: Map<Int, ByteArray>): CompletableFuture<IntArray>

    fun popChest(playerUid: UUID, num: Int): CompletableFuture<List<SlotDAO>?>

    fun getOrCreatePlayer(uuid: UUID): CompletableFuture<PlayerDAO>

    fun getWholeSnapshot(): CompletableFuture<DatabaseSnapshotDAO>

    fun setWholeSnapshot(snapshot: DatabaseSnapshotDAO): CompletableFuture<Int>

    fun renewChestExpiration(keys: Set<ChestKey>): CompletableFuture<IntArray>
}
