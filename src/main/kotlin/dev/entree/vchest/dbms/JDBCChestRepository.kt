package dev.entree.vchest.dbms

import com.zaxxer.hikari.HikariDataSource
import dev.entree.vchest.JDBCContext
import dev.entree.vchest.JDBCUtils
import dev.entree.vchest.chestPlugin
import dev.entree.vchest.futureTaskAsync
import org.bukkit.plugin.Plugin
import org.jooq.DSLContext
import org.jooq.DatePart
import org.jooq.Field
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.logging.Level

class JDBCChestRepository(
    private val plugin: Plugin,
    private val dataSource: HikariDataSource,
    private val sqlDialect: SQLDialect,
) : ChestRepository {
    companion object {
        @JvmStatic
        fun create(plugin: Plugin, ctx: JDBCContext): JDBCChestRepository {
            val dataSource = JDBCUtils.getDataSource(ctx)
            val sqlDialect = if (ctx.protocol == "sqlite") {
                SQLDialect.SQLITE
            } else {
                SQLDialect.MYSQL
            }
            System.setProperty("org.jooq.no-tips", "true")
            System.setProperty("org.jooq.no-logo", "true")
            JDBCUtils.initDatabase(dataSource, ctx.classLoader, ctx.protocol)
            return JDBCChestRepository(plugin, dataSource, sqlDialect)
        }

        fun onLockError(ex: Exception) {
            chestPlugin.logger.log(Level.WARNING, "Failed to acquire lock (this might not be an error)", ex)
        }

        fun getCurrentExpirationTime(): Field<LocalDateTime> {
            return DSL.localDateTimeAdd(DSL.currentLocalDateTime(), DSL.inline(chestPlugin.timeoutSecond), DatePart.SECOND)
        }
    }

    private fun <A> useDatabaseAsync(f: (DSLContext) -> A): CompletableFuture<A> {
        return plugin.futureTaskAsync {
            dataSource.connection.use { conn ->
                try {
                    f(DSL.using(conn, sqlDialect))
                } catch (ex: Exception) {
                    throw RuntimeException("Error while executing query", ex)
                }
            }
        }
    }

    override fun upsertChest(
        playerUuid: UUID,
        num: Int,
        items: Map<Int, ByteArray>,
    ): CompletableFuture<IntArray> {
        return useDatabaseAsync {
            if (it.dialect() == SQLDialect.SQLITE) {
                SQLiteQueries.upsertChest(it, playerUuid, num, items)
            } else {
                MySQLQueries.upsertChest(it, playerUuid, num, items)
            }
        }
    }

    override fun fetchChest(
        playerUuid: UUID,
        num: Int,
    ): CompletableFuture<List<SlotDAO>?> {
        return useDatabaseAsync {
            if (it.dialect() == SQLDialect.SQLITE) {
                SQLiteQueries.popChest(it, playerUuid, num)
            } else {
                MySQLQueries.popChest(it, playerUuid, num)
            }
        }
    }

    override fun getOrCreatePlayer(uuid: UUID): CompletableFuture<PlayerDAO> {
        return useDatabaseAsync {
            if (it.dialect() == SQLDialect.SQLITE) {
                SQLiteQueries.getOrCreatePlayer(it, uuid)
            } else {
                MySQLQueries.getOrCreatePlayer(it, uuid)
            }
        }
    }

    override fun getWholeSnapshot(): CompletableFuture<DatabaseSnapshotDAO> {
        return useDatabaseAsync {
            if (it.dialect() == SQLDialect.SQLITE) {
                SQLiteQueries.getWholeSnapshot(it)
            } else {
                MySQLQueries.getWholeSnapshot(it)
            }
        }
    }

    override fun setWholeSnapshot(snapshot: DatabaseSnapshotDAO): CompletableFuture<Int> {
        return useDatabaseAsync {
            if (it.dialect() == SQLDialect.SQLITE) {
                SQLiteQueries.setWholeSnapshot(it, snapshot)
            } else {
                MySQLQueries.setWholeSnapshot(it, snapshot)
            }
        }
    }

    override fun renewChestExpiration(
        keys: Set<ChestKey>,
    ): CompletableFuture<IntArray> {
        return useDatabaseAsync {
            if (it.dialect() == SQLDialect.SQLITE) {
                SQLiteQueries.renewChestExpiration(it, keys)
            } else {
                MySQLQueries.renewChestExpiration(it, keys)
            }
        }
    }

    override fun close() {
        dataSource.close()
    }
}
