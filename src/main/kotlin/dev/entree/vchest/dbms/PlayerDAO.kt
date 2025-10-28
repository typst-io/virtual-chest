package dev.entree.vchest.dbms

import java.util.UUID

data class PlayerDAO(
    val id: Int,
    val uuid: UUID,
    val name: String,
)
