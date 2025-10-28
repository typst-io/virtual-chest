package dev.entree.vchest.dbms

data class SlotDAO(
    val slot: Int,
    val chestNum: Int,
    val playerId: Int,
    val itemBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SlotDAO

        if (slot != other.slot) return false
        if (chestNum != other.chestNum) return false
        if (playerId != other.playerId) return false

        return true
    }

    override fun hashCode(): Int {
        var result = slot
        result = 31 * result + chestNum
        result = 31 * result + playerId
        return result
    }
}
