package dev.entree.vchest.dbms

import dev.entree.vchest.mysql.tables.records.ChestRecord
import dev.entree.vchest.mysql.tables.records.PlayerRecord
import dev.entree.vchest.mysql.tables.records.SlotRecord
import java.util.*

object MySQLObjects {
    fun fromPlayer(record: PlayerRecord): PlayerDAO {
        return PlayerDAO(record.playerId, UUID.fromString(record.playerUuid), record.playerName)
    }

    fun toPlayer(dao: PlayerDAO, record: PlayerRecord) {
        record.playerUuid = dao.uuid.toString()
        record.playerName = dao.name
    }

    fun fromChest(record: ChestRecord): ChestDAO {
        return ChestDAO(record.chestNum, record.chestPlayerId)
    }

    fun fromSlot(record: SlotRecord): SlotDAO {
        return SlotDAO(record.slotSlot, record.slotChestNum, record.slotPlayerId, record.slotItemBytes ?: ByteArray(0))
    }
}
