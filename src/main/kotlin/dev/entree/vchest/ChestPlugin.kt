package dev.entree.vchest

import dev.entree.vchest.cmd.ChestCommand
import dev.entree.vchest.config.PluginConfig
import dev.entree.vchest.config.PluginConfig.Companion.dbName
import dev.entree.vchest.dbms.ChestRepository
import dev.entree.vchest.dbms.JDBCChestRepository
import dev.entree.vchest.dbms.SQLEngine
import dev.entree.vchest.inventory.InventoryEngine
import io.typst.bukkit.kotlin.serialization.bukkitPluginJson
import io.typst.bukkit.kotlin.serialization.configJsonFile
import io.typst.command.bukkit.BukkitCommands
import org.bstats.bukkit.Metrics
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
import javax.swing.JFrame
import javax.swing.JOptionPane


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

    companion object {
        val perm: String = "virtualchest.op"
    }

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
            val newConfig = bukkitPluginJson.decodeFromString<PluginConfig>(text)
            val prevProtocol = pluginConfig.dbProtocol
            val newProtocol = newConfig.dbProtocol
            pluginConfig = newConfig
            if (prevProtocol != newProtocol) {
                repository.close()
                repository = JDBCChestRepository.create(this, getJDBCContext())
                logger.info("datasource changed from ${prevProtocol} to ${newProtocol}")
            }
            logger.info("config.json reloaded.")
        }, this).orElse(null)

        val jdbcCtx = getJDBCContext()
        repository = JDBCChestRepository.create(this, jdbcCtx)

        // events
        SQLEngine.register(this)
        BukkitCommands.register("창고", ChestCommand.node, ChestCommand::execute, this)
        InventoryEngine.register(this)
        System.setProperty("bstats.relocatecheck", "false")
        Metrics(this, 27796)
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
        runCatching {
            repository.close()
        }
    }

    fun getJDBCContext(config: PluginConfig = pluginConfig): JDBCContext {
        val ctx = when (config.dbProtocol) {
            "mysql" -> {
                JDBCContext.ofMySQL(
                    logger,
                    config.dbHost,
                    config.dbPort.toString(),
                    config.dbUsername,
                    config.dbPassword,
                    dbName
                )
            }

            "sqlite" -> {
                JDBCContext.ofSqlite(logger, dbName, dataFolder)
            }

            else -> {
                throw RuntimeException("Unknown sql protocol: ${config.dbProtocol}")
            }
        }
        return ctx.copy(classLoader = classLoader)
    }
}
