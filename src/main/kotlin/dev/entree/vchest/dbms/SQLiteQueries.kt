package dev.entree.vchest.dbms

import dev.entree.vchest.sqlite.Tables
import org.bukkit.Bukkit
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.*

object SQLiteQueries {
    fun upsertChest(ctx: DSLContext, uuid: UUID, num: Int, items: Map<Int, ByteArray>): IntArray {
        return ctx.transactionResult { trx ->
            val dsl = trx.dsl()

            val playerId = dsl.select(Tables.PLAYER.PLAYER_ID)
                .from(Tables.PLAYER)
                .where(Tables.PLAYER.PLAYER_UUID.eq(uuid.toString()))
                .fetchOne { it.value1() } ?: return@transactionResult IntArray(0)

            // upsert chest & unlock
            dsl
                .insertInto(
                    Tables.CHEST,
                    Tables.CHEST.CHEST_NUM,
                    Tables.CHEST.CHEST_PLAYER_ID,
                    Tables.CHEST.CHEST_EXPIRES_AT
                )
                .values(
                    DSL.value(num),
                    DSL.value(playerId),
                    DSL.value(null as? LocalDateTime),
                )
                .onConflict(
                    Tables.CHEST.CHEST_PLAYER_ID,
                    Tables.CHEST.CHEST_NUM
                )
                .doUpdate()
                .set(Tables.CHEST.CHEST_EXPIRES_AT, null as? LocalDateTime)
                .execute()

            if (items.isNotEmpty()) {
                val template = dsl
                    .insertInto(
                        Tables.SLOT,
                        Tables.SLOT.SLOT_SLOT,
                        Tables.SLOT.SLOT_CHEST_NUM,
                        Tables.SLOT.SLOT_PLAYER_ID,
                        Tables.SLOT.SLOT_ITEM_BYTES
                    )
                    .values(
                        DSL.param(Tables.SLOT.SLOT_SLOT.dataType),
                        DSL.param(Tables.SLOT.SLOT_CHEST_NUM),
                        DSL.param(Tables.SLOT.SLOT_PLAYER_ID),
                        DSL.param(Tables.SLOT.SLOT_ITEM_BYTES.dataType),
                    )
                    .onConflict(
                        Tables.SLOT.SLOT_SLOT,
                        Tables.SLOT.SLOT_CHEST_NUM,
                        Tables.SLOT.SLOT_PLAYER_ID,
                    )
                    .doUpdate()
                    .set(
                        Tables.SLOT.SLOT_ITEM_BYTES,
                        DSL.field("excluded.${Tables.SLOT.SLOT_ITEM_BYTES.name}", Tables.SLOT.SLOT_ITEM_BYTES.dataType)
                    )
                val batch = dsl.batch(template)

                for ((slot, itemBytes) in items) {
                    batch.bind(
                        slot,
                        num,
                        playerId,
                        itemBytes
                    )
                }
                batch.execute()
            } else intArrayOf(0)
        }
    }

    fun popChest(ctx: DSLContext, uuid: UUID, num: Int): List<SlotDAO>? {
        return ctx.transactionResult<List<SlotDAO>?> { trx ->
            val dsl = trx.dsl()
            val playerId = dsl.select(Tables.PLAYER.PLAYER_ID)
                .from(Tables.PLAYER)
                .where(Tables.PLAYER.PLAYER_UUID.eq(uuid.toString()))
                .fetchOne { it.value1() } ?: return@transactionResult emptyList()

            // lock
            val requiresAt = JDBCChestRepository.getCurrentExpirationTime()
            val lockUpdates =
                try {
                    dsl
                        .insertInto(
                            Tables.CHEST,
                            Tables.CHEST.CHEST_NUM,
                            Tables.CHEST.CHEST_PLAYER_ID,
                            Tables.CHEST.CHEST_EXPIRES_AT,
                        )
                        .values(
                            DSL.value(num),
                            DSL.value(playerId),
                            requiresAt
                        )
                        .onConflict(Tables.CHEST.CHEST_NUM, Tables.CHEST.CHEST_PLAYER_ID)
                        .doUpdate()
                        .set(
                            Tables.CHEST.CHEST_EXPIRES_AT,
                            DSL.case_()
                                .`when`(
                                    Tables.CHEST.CHEST_EXPIRES_AT.isNull
                                        .or(Tables.CHEST.CHEST_EXPIRES_AT.lessOrEqual(DSL.currentLocalDateTime())),
                                    requiresAt
                                )
                                .otherwise(Tables.CHEST.CHEST_EXPIRES_AT)
                        )
                        .execute()
                } catch (ex: Exception) {
                    JDBCChestRepository.onLockError(ex)
                    0
                }
            if (lockUpdates <= 0) {
                return@transactionResult null
            }

            val slots = dsl.select(Tables.SLOT.asterisk())
                .from(Tables.SLOT)
                .where(Tables.SLOT.SLOT_CHEST_NUM.eq(num))
                .and(Tables.SLOT.SLOT_PLAYER_ID.eq(playerId))
                .fetchInto(Tables.SLOT)

            dsl.deleteFrom(Tables.SLOT)
                .where(Tables.SLOT.SLOT_PLAYER_ID.eq(playerId))
                .and(Tables.SLOT.SLOT_CHEST_NUM.eq(num))
                .execute()
            slots.map(SQLiteObjects::fromSlot)
        }
    }

    fun getOrCreatePlayer(
        ctx: DSLContext,
        uuid: UUID,
    ): PlayerDAO {
        val name = Bukkit.getPlayer(uuid)?.name
        return ctx.transactionResult { trx ->
            val dsl = trx.dsl()
            val prevPlayer = dsl.selectFrom(Tables.PLAYER)
                .where(Tables.PLAYER.PLAYER_UUID.eq(uuid.toString()))
                .fetchOne()
            val player = if (prevPlayer != null) {
                if (name != null) {
                    prevPlayer.playerName = name
                }
                prevPlayer.update()
                prevPlayer
            } else {
                dsl.newRecord(Tables.PLAYER).apply {
                    playerName = name ?: ""
                    playerUuid = uuid.toString()
                    store()
                }
            }
            SQLiteObjects.fromPlayer(player)
        }
    }

    fun getWholeSnapshot(ctx: DSLContext): DatabaseSnapshotDAO {
        val players = ctx.selectFrom(Tables.PLAYER)
            .fetch { SQLiteObjects.fromPlayer(it) }
        val chests = ctx.selectFrom(Tables.CHEST)
            .fetch { SQLiteObjects.fromChest(it) }
        val slots = ctx.selectFrom(Tables.SLOT)
            .fetch { SQLiteObjects.fromSlot(it) }
        return DatabaseSnapshotDAO(players, chests, slots)
    }

    fun setWholeSnapshot(ctx: DSLContext, x: DatabaseSnapshotDAO): Int {
        return ctx.transactionResult { trx ->
            val dsl = trx.dsl()

            // players
            val playerTemplate = dsl
                .insertInto(
                    Tables.PLAYER,
                    Tables.PLAYER.PLAYER_ID,
                    Tables.PLAYER.PLAYER_UUID,
                    Tables.PLAYER.PLAYER_NAME
                )
                .values(
                    DSL.param(Tables.PLAYER.PLAYER_ID.dataType),
                    DSL.param(Tables.PLAYER.PLAYER_UUID.dataType),
                    DSL.param(Tables.PLAYER.PLAYER_NAME)
                )
                .onConflict(Tables.PLAYER.PLAYER_UUID)
                .doNothing()
            val playerBatch = dsl.batch(playerTemplate)
            for (player in x.players) {
                playerBatch.bind(
                    player.id,
                    player.uuid,
                    player.name
                )
            }

            // chests
            val chestTemplate = dsl
                .insertInto(
                    Tables.CHEST,
                    Tables.CHEST.CHEST_PLAYER_ID,
                    Tables.CHEST.CHEST_NUM
                )
                .values(
                    DSL.param(Tables.CHEST.CHEST_PLAYER_ID),
                    DSL.param(Tables.CHEST.CHEST_NUM),
                )
                .onConflict(
                    Tables.CHEST.CHEST_PLAYER_ID,
                    Tables.CHEST.CHEST_NUM
                )
                .doNothing()
            val chestBatch = dsl.batch(chestTemplate)
            for (chest in x.chests) {
                chestBatch.bind(
                    chest.playerId,
                    chest.num
                )
            }

            // slots
            dsl.deleteFrom(Tables.SLOT)
                .execute()
            val slotTemplate = dsl
                .insertInto(
                    Tables.SLOT,
                    Tables.SLOT.SLOT_PLAYER_ID,
                    Tables.SLOT.SLOT_CHEST_NUM,
                    Tables.SLOT.SLOT_SLOT,
                    Tables.SLOT.SLOT_ITEM_BYTES
                )
                .values(
                    DSL.param(Tables.SLOT.SLOT_PLAYER_ID.dataType),
                    DSL.param(Tables.SLOT.SLOT_CHEST_NUM.dataType),
                    DSL.param(Tables.SLOT.SLOT_SLOT.dataType),
                    DSL.param(Tables.SLOT.SLOT_ITEM_BYTES.dataType),
                )
                .onConflict(
                    Tables.SLOT.SLOT_PLAYER_ID,
                    Tables.SLOT.SLOT_CHEST_NUM,
                    Tables.SLOT.SLOT_SLOT,
                )
                .doUpdate()
                .set(
                    Tables.SLOT.SLOT_ITEM_BYTES,
                    DSL.field("excluded.${Tables.SLOT.SLOT_ITEM_BYTES.name}", Tables.SLOT.SLOT_ITEM_BYTES.dataType)
                )
            val slotBatch = dsl.batch(slotTemplate)
            for (slot in x.slots) {
                slotBatch.bind(
                    slot.playerId,
                    slot.chestNum,
                    slot.slot,
                    slot.itemBytes
                )
            }

            val playerBatchResult = playerBatch.execute()
            val chestBatchResult = chestBatch.execute()
            val slotBatchResult = slotBatch.execute()
            playerBatchResult.size + chestBatchResult.size + slotBatchResult.size
        }
    }

    fun renewChestExpiration(ctx: DSLContext, keys: Set<ChestKey>): IntArray {
        if (keys.isEmpty()) {
            return intArrayOf()
        }
        return ctx.transactionResult { trx ->
            val dsl = trx.dsl()

            val renewTemplate = dsl
                .update(
                    Tables.CHEST
                        .join(Tables.PLAYER)
                        .on(Tables.PLAYER.PLAYER_ID.eq(Tables.CHEST.CHEST_PLAYER_ID))
                )
                .set(Tables.CHEST.CHEST_EXPIRES_AT, JDBCChestRepository.getCurrentExpirationTime())
                .where(
                    Tables.PLAYER.PLAYER_UUID.eq(DSL.param(Tables.PLAYER.PLAYER_UUID.dataType))
                        .and(Tables.CHEST.CHEST_EXPIRES_AT.isNotNull)
                        .and(Tables.CHEST.CHEST_NUM.eq(DSL.param(Tables.CHEST.CHEST_NUM.dataType)))
                )
            val batch = dsl.batch(renewTemplate)
            for (key in keys) {
                batch.bind(
                    key.playerUid.toString(),
                    key.num
                )
            }
            batch.execute()
        }
    }
}
