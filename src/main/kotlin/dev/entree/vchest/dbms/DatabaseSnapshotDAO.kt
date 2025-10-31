package dev.entree.vchest.dbms

data class DatabaseSnapshotDAO(
    val players: List<PlayerDAO>,
    val chests: List<ChestDAO>,
    val slots: List<SlotDAO>,
)