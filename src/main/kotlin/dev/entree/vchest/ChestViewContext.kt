package dev.entree.vchest

import java.util.*

data class ChestViewContext(
    val owner: UUID,
    val num: Int,
    val viewer: UUID,
)
