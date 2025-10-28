package dev.entree.vchest.dbms

import com.zaxxer.hikari.HikariDataSource
import dev.entree.vchest.JDBCContext
import dev.entree.vchest.JDBCUtils
import dev.entree.vchest.futureTaskAsync
import org.bukkit.plugin.Plugin
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.util.*
import java.util.concurrent.CompletableFuture

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
    }

    private fun <A> useDatabaseAsync(f: (DSLContext) -> A): CompletableFuture<A> {
        val (_, future) = plugin.futureTaskAsync {
            dataSource.connection.use { conn ->
                try {
                    f(DSL.using(conn, sqlDialect))
                } catch (ex: Exception) {
                    throw RuntimeException("Error while executing query", ex)
                }
            }
        }
        return future
    }

    override fun upsertChest(
        uuid: UUID,
        num: Int,
        items: Map<Int, ByteArray>,
    ): CompletableFuture<IntArray> {
        return useDatabaseAsync {
            if (it.dialect() == SQLDialect.SQLITE) {
                SQLiteQueries.upsertChest(it, uuid, num, items)
            } else {
                MySQLQueries.upsertChest(it, uuid, num, items)
            }
        }
    }

    override fun popChest(
        uuid: UUID,
        num: Int,
    ): CompletableFuture<List<SlotDAO>> {
        return useDatabaseAsync {
            if (it.dialect() == SQLDialect.SQLITE) {
                SQLiteQueries.popChest(it, uuid, num)
            } else {
                MySQLQueries.popChest(it, uuid, num)
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

    override fun close() {
        dataSource.close()
    }
}
