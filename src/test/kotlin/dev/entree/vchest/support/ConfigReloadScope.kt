package dev.entree.vchest.support

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import dev.entree.vchest.FileChangeListener
import dev.entree.vchest.JDBCUtils
import dev.entree.vchest.config.PluginConfig
import io.typst.bukkit.kotlin.serialization.bukkitPluginYaml
import io.typst.command.bukkit.BukkitCommands
import kotlinx.serialization.encodeToString
import org.bstats.bukkit.Metrics
import org.bukkit.plugin.Plugin
import org.bukkit.scheduler.BukkitTask
import org.mockito.Mockito.*
import java.io.Closeable
import java.nio.file.Path
import java.sql.SQLException
import java.util.*
import java.util.function.Consumer

/** Invokes the real onEnable and its real file-reload callback, intercepting external resources. */
class ConfigReloadScope(directory: Path, initial: PluginConfig = PluginConfig(dbPassword = "test-password")) : Closeable {
    val scope = PluginTestScope(directory.toFile())
    val configurations = mutableListOf<HikariConfig>()
    val pools = mutableListOf<HikariDataSource>()
    val closedPools = mutableSetOf<HikariDataSource>()
    private val resources = mutableListOf<AutoCloseable>(scope)
    private lateinit var reloadListener: Consumer<String>

    init {
        try {
            resources += mockStatic(JDBCUtils::class.java, CALLS_REAL_METHODS).apply {
                `when`<Unit> { JDBCUtils.initDatabase(any(HikariDataSource::class.java), any(), anyString()) }
                    .thenAnswer { null }
            }
            resources += mockConstruction(HikariDataSource::class.java) { pool, context ->
                pools += pool
                configurations += context.arguments().single() as HikariConfig
                doAnswer { closedPools += pool; null }.`when`(pool).close()
                `when`(pool.connection).thenThrow(SQLException("database boundary mocked in lifecycle tests"))
            }
            resources += mockStatic(FileChangeListener::class.java).apply {
                `when`<Optional<Closeable>> {
                    FileChangeListener.registerPrime(eq("config.yml"), any(), eq(scope.plugin))
                }.thenAnswer {
                    reloadListener = it.getArgument(1)
                    Optional.empty<Closeable>()
                }
            }
            resources += mockStatic(BukkitCommands::class.java)
            resources += mockConstruction(Metrics::class.java)
            val task = mock(BukkitTask::class.java)
            `when`(scope.scheduler.runTaskTimer(any(Plugin::class.java), any(Runnable::class.java), anyLong(), anyLong()))
                .thenReturn(task)
            scope.plugin.configFile.writeText(bukkitPluginYaml.encodeToString(initial))
            scope.plugin.onEnable()
        } catch (failure: Throwable) {
            close()
            throw failure
        }
    }

    fun reload(config: PluginConfig) = reloadListener.accept(bukkitPluginYaml.encodeToString(config))

    override fun close() {
        resources.asReversed().forEach { it.close() }
    }
}
