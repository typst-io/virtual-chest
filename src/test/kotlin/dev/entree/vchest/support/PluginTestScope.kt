package dev.entree.vchest.support

import dev.entree.vchest.ChestPlugin
import dev.entree.vchest.ChestViewContext
import dev.entree.vchest.config.PluginConfig
import dev.entree.vchest.dbms.ChestRepository
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.scheduler.BukkitScheduler
import org.mockito.Mockito.*
import java.io.Closeable
import java.io.File
import java.util.*
import java.util.concurrent.Executor
import java.util.logging.Level
import java.util.logging.Logger

/** Only Bukkit boundaries are mocked; plugin methods and query implementations remain real. */
class PluginTestScope(dataFolder: File? = null) : Closeable {
    val plugin: ChestPlugin = mock(ChestPlugin::class.java, CALLS_REAL_METHODS)
    val repository: ChestRepository = mock(ChestRepository::class.java)
    val players = mutableMapOf<UUID, Player>()
    val openingChests = mutableMapOf<UUID, ChestViewContext>()
    val manager: PluginManager = mock(PluginManager::class.java)
    val scheduler: BukkitScheduler = mock(BukkitScheduler::class.java)
    val bukkit = mockStatic(Bukkit::class.java)
    private val javaPlugin = mockStatic(JavaPlugin::class.java)

    init {
        val logger = Logger.getAnonymousLogger().apply { level = Level.OFF }
        doReturn(logger).`when`(plugin).logger
        doReturn(false).`when`(plugin).isEnabled
        if (dataFolder != null) doReturn(dataFolder).`when`(plugin).dataFolder
        setField("openingChests", openingChests)
        setField("syncExecutor", Executor(Runnable::run))
        plugin.pluginConfig = PluginConfig(dbPassword = "test-password")
        plugin.timeoutSecond = 60
        plugin.repository = repository
        javaPlugin.`when`<ChestPlugin> { JavaPlugin.getPlugin(ChestPlugin::class.java) }.thenReturn(plugin)
        bukkit.`when`<PluginManager> { Bukkit.getPluginManager() }.thenReturn(manager)
        bukkit.`when`<BukkitScheduler> { Bukkit.getScheduler() }.thenReturn(scheduler)
        bukkit.`when`<Collection<Player>> { Bukkit.getOnlinePlayers() }.thenAnswer { players.values.toList() }
        bukkit.`when`<Player?> { Bukkit.getPlayer(any(UUID::class.java)) }
            .thenAnswer { players[it.getArgument<UUID>(0)] }
    }

    fun player(id: UUID = UUID.randomUUID()): Player = mock(Player::class.java).also {
        `when`(it.uniqueId).thenReturn(id)
        `when`(it.isOnline).thenReturn(true)
        players[id] = it
    }

    private fun setField(name: String, value: Any) {
        ChestPlugin::class.java.getDeclaredField(name).apply { isAccessible = true }.set(plugin, value)
    }

    override fun close() {
        javaPlugin.close()
        bukkit.close()
    }
}
