package dev.entree.vchest

import java.util.UUID

data class ChestViewContext(
    val owner: UUID,
    val num: Int,
    val viewer: UUID,
)
