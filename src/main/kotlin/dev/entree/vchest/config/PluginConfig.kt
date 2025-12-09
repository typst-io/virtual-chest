package dev.entree.vchest.config

import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class PluginConfig(
    val dbProtocol: String = "sqlite",
    val dbHost: String = "localhost",
    val dbPort: Int = 3306,
    val dbUsername: String = "root",
    val dbPassword: String = System.getenv("MYSQL_PW") ?: "password",
    val chestTitle: String = "Chest %num%",
    val chestSizeRow: Int = 6,
    val noPermissionMessage: String = "§cNo permission: %perm%",
    val alreadyOpenedChestMessage: String = "§cThis chest is already open(locked)!",
    val overrideLocale: String = "",
) {
    val locale: String
        get() = overrideLocale.ifEmpty { Locale.getDefault().toString().lowercase() }

    companion object {
        const val dbName: String = "mc_virtual_chest"
    }
}
