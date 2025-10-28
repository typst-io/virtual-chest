package dev.entree.vchest.dbms

import java.lang.AutoCloseable
import java.util.*
import java.util.concurrent.CompletableFuture

interface ChestRepository : AutoCloseable {
    fun upsertChest(uuid: UUID, num: Int, items: Map<Int, ByteArray>): CompletableFuture<IntArray>

    fun popChest(uuid: UUID, num: Int): CompletableFuture<List<SlotDAO>>

    fun getOrCreatePlayer(uuid: UUID): CompletableFuture<PlayerDAO>
}
