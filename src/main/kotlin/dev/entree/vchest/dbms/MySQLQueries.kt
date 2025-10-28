package dev.entree.vchest.dbms

import dev.entree.vchest.mysql.Tables
import org.bukkit.Bukkit
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.util.*

object MySQLQueries {
    fun upsertChest(ctx: DSLContext, uuid: UUID, num: Int, items: Map<Int, ByteArray>): IntArray {
        return ctx.transactionResult { trx ->
            val dsl = trx.dsl()

            val playerId = dsl.select(Tables.PLAYER.PLAYER_ID)
                .from(Tables.PLAYER)
                .where(Tables.PLAYER.PLAYER_UUID.eq(uuid.toString()))
                .fetchOne { it.value1() } ?: return@transactionResult IntArray(0)

            // upsert chest
            dsl
                .insertInto(
                    Tables.CHEST,
                    Tables.CHEST.CHEST_NUM,
                    Tables.CHEST.CHEST_PLAYER_ID
                )
                .values(
                    DSL.value(num),
                    DSL.value(playerId)
                )
                .onDuplicateKeyIgnore()
                .execute()

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
                .onDuplicateKeyUpdate()
                .set(
                    Tables.SLOT.SLOT_ITEM_BYTES,
                    DSL.field("VALUES(slot_item_bytes)", Tables.SLOT.SLOT_ITEM_BYTES.dataType)
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
        }
    }

    fun popChest(ctx: DSLContext, uuid: UUID, num: Int): List<SlotDAO> {
        return ctx.transactionResult { trx ->
            val dsl = trx.dsl()

            val playerId = dsl.select(Tables.PLAYER.PLAYER_ID)
                .from(Tables.PLAYER)
                .where(Tables.PLAYER.PLAYER_UUID.eq(uuid.toString()))
                .fetchOne { it.value1() } ?: return@transactionResult emptyList()

            val slots = if (trx.dialect() == SQLDialect.SQLITE) {
                dsl.select(Tables.SLOT.asterisk())
                    .from(Tables.SLOT)
                    .where(Tables.SLOT.SLOT_CHEST_NUM.eq(num))
                    .and(Tables.SLOT.SLOT_PLAYER_ID.eq(playerId))
                    .fetchInto(Tables.SLOT)
            } else {
                dsl.select(Tables.SLOT.asterisk())
                    .from(Tables.SLOT)
                    .where(Tables.SLOT.SLOT_CHEST_NUM.eq(num))
                    .and(Tables.SLOT.SLOT_PLAYER_ID.eq(playerId))
                    .forUpdate()
                    .fetchInto(Tables.SLOT)
            }

            dsl.deleteFrom(Tables.SLOT)
                .where(Tables.SLOT.SLOT_PLAYER_ID.eq(playerId))
                .and(Tables.SLOT.SLOT_CHEST_NUM.eq(num))
                .execute()
            slots.map(MySQLObjects::fromSlot)
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
                .forUpdate()
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
            MySQLObjects.fromPlayer(player)
        }
    }
}
