package dev.entree.vchest

import dev.entree.vchest.cmd.ChestCommand
import dev.entree.vchest.config.PluginConfig
import dev.entree.vchest.dbms.ChestRepository
import dev.entree.vchest.dbms.JDBCChestRepository
import dev.entree.vchest.dbms.SQLEngine
import dev.entree.vchest.inventory.InventoryEngine
import io.typst.bukkit.kotlin.serialization.bukkitPluginJson
import io.typst.bukkit.kotlin.serialization.configJsonFile
import io.typst.command.bukkit.BukkitCommands
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.Closeable
import java.io.File
import java.text.NumberFormat
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.logging.Level

fun nfmt(number: Number): String =
    NumberFormat.getNumberInstance().format(number)

val chestPlugin: ChestPlugin get() = JavaPlugin.getPlugin(ChestPlugin::class.java)

fun <A> CompletableFuture<A>.thenAcceptSync(f: (A) -> Unit) {
    val f2: (A?, Throwable?) -> Unit = { a, th ->
        if (th != null) {
            chestPlugin.logger.log(Level.WARNING, "Error while accepting", th)
        } else if (a != null) {
            try {
                f(a)
            } catch (th: Throwable) {
                chestPlugin.logger.log(Level.WARNING, "Error while accepting sync", th)
            }
        }
    }
    if (chestPlugin.isEnabled) {
        whenCompleteAsync(f2, chestPlugin.syncExecutor)
    } else {
        whenComplete(f2)
    }
}

class ChestPlugin : JavaPlugin() {
    val configFile: File get() = File(dataFolder, "config.json")
    var pluginConfig: PluginConfig = PluginConfig()
    var fileListener: Closeable? = null
    val syncExecutor: Executor = Executor { command ->
        runTask(0, command::run)
    }
    val openingChests: MutableMap<UUID, ChestViewContext> = mutableMapOf()
    lateinit var repository: ChestRepository

    override fun onEnable() {
        // config
        if (configFile.isFile) {
            pluginConfig = bukkitPluginJson.decodeFromString<PluginConfig>(configJsonFile.readText())
        } else {
            configFile.parentFile.mkdirs()
            configFile.writeText(bukkitPluginJson.encodeToString<PluginConfig>(pluginConfig))
        }
        dataFolder.resolve("example-config.json").writeText(bukkitPluginJson.encodeToString(pluginConfig))
        fileListener = FileChangeListener.registerPrime("config.json", { text ->
            pluginConfig = bukkitPluginJson.decodeFromString<PluginConfig>(text)
            logger.info("config.json reloaded.")
        }, this).orElse(null)

        val dbName = "mc_virtual_chest"
        val jdbcCtx = if (pluginConfig.dbProtocol == "mysql") {
            JDBCContext.ofMySQL(
                logger,
                pluginConfig.dbHost,
                pluginConfig.dbPort.toString(),
                pluginConfig.dbUsername,
                pluginConfig.dbPassword,
                dbName
            )
        } else if (pluginConfig.dbProtocol == "sqlite") {
            JDBCContext.ofSqlite(logger, dbName, dataFolder)
        } else {
            throw RuntimeException("Unknown sql protocol: ${pluginConfig.dbProtocol}")
        }
        repository = JDBCChestRepository.create(this, jdbcCtx)

        // events
        SQLEngine.register(this)
        BukkitCommands.register("창고", ChestCommand.node, ChestCommand::execute, this)
        InventoryEngine.register(this)
    }

    override fun onDisable() {
        runCatching { fileListener?.close() }
        for (p in Bukkit.getOnlinePlayers()) {
            val chestCtx = openingChests[p.uniqueId] ?: continue
            val items = mutableMapOf<Int, ItemStack>()
            for ((index, stack) in p.openInventory.topInventory.contents.withIndex()) {
                stack ?: continue
                if (stack.type == Material.AIR) continue
                items[index] = ItemStack(stack)
            }
            ChestCommand.saveChest(p, chestCtx.num, items, repository)
        }
        runCatching { repository.close() }
    }
}
