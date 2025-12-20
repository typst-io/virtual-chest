package dev.entree.vchest

import dev.entree.vchest.cmd.ChestCommand
import dev.entree.vchest.config.PluginConfig
import dev.entree.vchest.config.PluginConfig.Companion.dbName
import dev.entree.vchest.dbms.ChestRepository
import dev.entree.vchest.dbms.JDBCChestRepository
import dev.entree.vchest.dbms.SQLEngine
import dev.entree.vchest.heartbeat.HeartbeatEngine
import dev.entree.vchest.inventory.InventoryEngine
import io.typst.bukkit.kotlin.serialization.bukkitPluginYaml
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.bstats.bukkit.Metrics
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import java.io.Closeable
import java.io.File
import java.text.NumberFormat
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.logging.Level


fun nfmt(number: Number): String =
    NumberFormat.getNumberInstance().format(number)

val chestPlugin: ChestPlugin get() = JavaPlugin.getPlugin(ChestPlugin::class.java)

fun <A, B> CompletableFuture<A>.thenApplySync(f: (A) -> B): CompletableFuture<B> {
    return thenApplyAsync(f, chestPlugin.syncExecutor)
}

fun <A> CompletableFuture<A>.thenAcceptSyncPrime(f: (Result<A>) -> Unit): CompletableFuture<A> {
    val f2: (A, Throwable?) -> Unit = { a, th ->
        if (th != null) {
            chestPlugin.logger.log(Level.WARNING, "Error while accepting", th)
        } else {
            try {
                f(Result.success(a))
            } catch (th: Throwable) {
                chestPlugin.logger.log(Level.WARNING, "Error while accepting sync", th)
                f(Result.failure(th))
            }
        }
    }
    return if (chestPlugin.isEnabled) {
        whenCompleteAsync(f2, chestPlugin.syncExecutor)
    } else {
        whenComplete(f2)
    }
}

fun <A> CompletableFuture<A>.thenAcceptSync(f: (A) -> Unit): CompletableFuture<A> {
    return thenAcceptSyncPrime {
        if (it.isSuccess) {
            f(it.getOrThrow())
        }
    }
}

class ChestPlugin : JavaPlugin() {
    val configFile: File get() = File(dataFolder, "config.yml")
    var pluginConfig: PluginConfig = PluginConfig()
    var fileListener: Closeable? = null
    val syncExecutor: Executor = Executor { command ->
        runTask(0, command::run)
    }
    val openingChests: MutableMap<UUID, ChestViewContext> = mutableMapOf()
    var lastTick: LocalDateTime = LocalDateTime.now()
    var timeoutSecond: Int = 60
    lateinit var repository: ChestRepository

    companion object {
        val perm: String = "virtualchest.op"
    }

    override fun onEnable() {
        // config
        timeoutSecond = try {
            Bukkit.spigot().spigotConfig.getInt("settings.timeout-time", -1).takeIf { it > 0 }
        } catch (_: Exception) {
            null
        } ?: 60
        if (configFile.isFile) {
            pluginConfig = bukkitPluginYaml.decodeFromString<PluginConfig>(configFile.readText())
        } else {
            configFile.parentFile.mkdirs()
            configFile.writeText(bukkitPluginYaml.encodeToString<PluginConfig>(pluginConfig))
        }
        dataFolder.resolve("example-config.yml").writeText(bukkitPluginYaml.encodeToString(pluginConfig))
        fileListener = FileChangeListener.registerPrime("config.yml", { text ->
            val newConfig = bukkitPluginYaml.decodeFromString<PluginConfig>(text)
            if (pluginConfig.dbProtocol != newConfig.dbProtocol) {
                repository.close()
                repository = JDBCChestRepository.create(this, getJDBCContext())
                logger.info("datasource changed from ${pluginConfig.dbProtocol} to ${newConfig.dbProtocol}")
            }
            pluginConfig = newConfig
            logger.info("config.yml reloaded.")
        }, this).orElse(null)

        val jdbcCtx = getJDBCContext()
        repository = JDBCChestRepository.create(this, jdbcCtx)

        // events
        ChestCommand.register(this)
        SQLEngine.register(this)
        InventoryEngine.register(this)
        System.setProperty("bstats.relocatecheck", "false")
        Metrics(this, 27796)
        HeartbeatEngine.register(this)
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
            try {
                ChestCommand.saveChest(p, chestCtx.num, items, repository).thenAcceptSync {
                    // nothing
                }
            } catch (ex: Exception) {
                logger.log(Level.WARNING, "Error while saving chests on disable", ex)
            }
        }
        runCatching { repository.close() }
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
