package dev.entree.vchest.config

import kotlinx.serialization.Serializable

@Serializable
data class PluginConfig(
    val dbProtocol: String = "mysql",
    val dbHost: String = "localhost",
    val dbPort: Int = 3306,
    val dbUsername: String = "root",
    val dbPassword: String = System.getenv("MYSQL_PW") ?: "password",
    val chestTitle: String = "%num% 번 창고",
    val chestSizeRow: Int = 6,
)
