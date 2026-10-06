package dev.entree.vchest.dbms

import com.zaxxer.hikari.HikariDataSource
import dev.entree.vchest.cmd.ChestCommand
import dev.entree.vchest.support.DatabaseFixture
import dev.entree.vchest.support.IsolatedMySQL
import dev.entree.vchest.support.PluginTestScope
import org.bukkit.inventory.ItemStack
import org.jooq.SQLDialect
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.io.TempDir
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.nio.file.Path
import java.sql.SQLException
import java.util.*
import java.util.concurrent.CompletionException

@Tag("mysql")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MySQLRegressionTest {
    private lateinit var server: IsolatedMySQL

    @BeforeAll
    fun startServer(@TempDir directory: Path) { server = IsolatedMySQL.start(directory) }

    @AfterAll
    fun stopServer() { if (::server.isInitialized) server.close() }

    private fun database(): DatabaseFixture = DatabaseFixture(server.connection(), SQLDialect.MYSQL).apply {
        // Safe: server was freshly created by this test class and is never an existing service.
        dsl.execute("DROP DATABASE mc_virtual_chest")
        dsl.execute("CREATE DATABASE mc_virtual_chest")
        dsl.execute("USE mc_virtual_chest")
        migrate()
    }

    @Test
    fun `an unexpired chest lease rejects a second open`() {
        PluginTestScope().use {
            database().use { db ->
                val owner = UUID.randomUUID()
                db.seedPlayer(owner)
                db.save(owner, mapOf(0 to byteArrayOf(42)))
                assertArrayEquals(byteArrayOf(42), db.open(owner)!!.single().itemBytes)
                assertNull(db.open(owner), "useAffectedRows=true must reject an unchanged lock row")
            }
        }
    }

    @Test
    fun `a previous lease holder cannot overwrite the next holders save`() {
        PluginTestScope().use {
            database().use { db ->
                val owner = UUID.randomUUID()
                val oldContents = byteArrayOf(42)
                val newContents = byteArrayOf(99)
                db.seedPlayer(owner)
                db.save(owner, mapOf(0 to oldContents))
                val serverAContents = db.open(owner)!!.single().itemBytes
                db.expireLease()
                assertNotNull(db.open(owner), "Server B must acquire the expired lease")
                db.save(owner, mapOf(0 to newContents))
                // A resumes after B commits; its old session must no longer have write authority.
                runCatching { db.save(owner, mapOf(0 to serverAContents)) }
                assertArrayEquals(newContents, db.slotBytes(), "An expired session must not overwrite B's saved contents")
            }
        }
    }

    @Test
    fun `an old heartbeat cannot renew another servers lease`() {
        PluginTestScope().use {
            database().use { db ->
                val owner = UUID.randomUUID()
                db.seedPlayer(owner)
                db.save(owner, mapOf(0 to byteArrayOf(42)))
                assertNotNull(db.open(owner))
                db.expireLease()
                assertNotNull(db.open(owner))
                db.dsl.execute("UPDATE chest SET chest_expires_at = '2035-01-01 00:00:00'")
                val before = db.dsl.fetchOne("SELECT chest_expires_at FROM chest")!!.get(0)
                MySQLQueries.renewChestExpiration(db.dsl, setOf(ChestKey(1, owner)))
                val after = db.dsl.fetchOne("SELECT chest_expires_at FROM chest")!!.get(0)
                assertEquals(before, after, "Heartbeat from expired session A must not modify B's lease")
            }
        }
    }

    @Test
    fun `a failed close must not make a withdrawn item available again`() {
        PluginTestScope().use { scope ->
            database().use { db ->
                val owner = UUID.randomUUID()
                db.seedPlayer(owner)
                db.save(owner, mapOf(0 to byteArrayOf(42)))
                val withdrawnItem = db.open(owner)!!.single().itemBytes
                val player = scope.player(owner)
                val unavailablePool = mock(HikariDataSource::class.java)
                `when`(unavailablePool.connection).thenThrow(SQLException("simulated connection outage"))
                val unavailableRepository = JDBCChestRepository(scope.plugin, unavailablePool, SQLDialect.MYSQL)
                val close = ChestCommand.saveChest(player, 1, emptyMap<Int, ItemStack>(), unavailableRepository)
                assertThrows(CompletionException::class.java) { close.join() }
                db.expireLease()
                val nextOpen = db.open(owner)!!
                assertFalse(nextOpen.any { it.itemBytes.contentEquals(withdrawnItem) },
                    "The item is already in the player's possession; exposing its old DB copy duplicates it")
            }
        }
    }

    @Test
    fun `migration into a populated database preserves UUID ownership`() {
        database().use { db ->
            val owner = UUID.randomUUID()
            db.seedPlayer(UUID.randomUUID(), 1, "other")
            db.seedPlayer(owner, 7)
            db.restore(DatabaseSnapshotDAO(
                listOf(PlayerDAO(1, owner, "owner")),
                listOf(ChestDAO(1, 1)),
                listOf(SlotDAO(0, 1, 1, byteArrayOf(42)))
            ))
            assertArrayEquals(byteArrayOf(42), db.slotBytes())
            assertEquals(owner, db.slotOwner(), "UUID ownership must survive different auto-increment IDs")
        }
    }

    @Test
    fun `saving and reopening an ordinary chest preserves its item bytes`() {
        PluginTestScope().use {
            database().use { db ->
                val owner = UUID.randomUUID()
                db.seedPlayer(owner)
                db.save(owner, mapOf(0 to byteArrayOf(42)))
                assertNotNull(db.open(owner))
                db.save(owner, mapOf(0 to byteArrayOf(99)))
                assertArrayEquals(byteArrayOf(99), db.open(owner)!!.single().itemBytes)
            }
        }
    }
}
