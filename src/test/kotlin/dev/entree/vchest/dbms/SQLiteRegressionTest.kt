package dev.entree.vchest.dbms

import dev.entree.vchest.support.DatabaseFixture
import dev.entree.vchest.support.PluginTestScope
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.util.*

class SQLiteRegressionTest {
    @TempDir lateinit var directory: Path

    @Test
    fun `an unexpired chest lease rejects a second open`() {
        PluginTestScope().use {
            DatabaseFixture.sqlite(directory.resolve("lock.db")).use { db ->
                db.migrate()
                val owner = UUID.randomUUID()
                db.seedPlayer(owner)
                db.save(owner, mapOf(0 to byteArrayOf(42)))
                assertEquals(1, db.open(owner)!!.size, "The first open must succeed")
                assertNull(db.open(owner), "An unexpired lease must reject the second reader")
            }
        }
    }

    @Test
    fun `opening a chest retains a durable copy until its contents are saved`() {
        PluginTestScope().use {
            DatabaseFixture.sqlite(directory.resolve("durable.db")).use { db ->
                db.migrate()
                val owner = UUID.randomUUID()
                val item = byteArrayOf(42)
                db.seedPlayer(owner)
                db.save(owner, mapOf(0 to item))
                assertArrayEquals(item, db.open(owner)!!.single().itemBytes)
                assertArrayEquals(item, db.slotBytes(), "A crash before close must not lose the only durable copy")
            }
        }
    }

    @Test
    fun `a restart after an unsaved open can recover the stored items`() {
        PluginTestScope().use {
            val path = directory.resolve("restart.db")
            val owner = UUID.randomUUID()
            val item = byteArrayOf(42)
            DatabaseFixture.sqlite(path).use { db ->
                db.migrate()
                db.seedPlayer(owner)
                db.save(owner, mapOf(0 to item))
                assertEquals(1, db.open(owner)!!.size)
            }
            DatabaseFixture.sqlite(path).use { restarted ->
                restarted.expireLease()
                val recovered = restarted.open(owner)!!
                assertEquals(1, recovered.size, "Reopening after a crash must recover the stored item")
                assertArrayEquals(item, recovered.single().itemBytes)
            }
        }
    }

    @Test
    fun `migration into a populated database preserves UUID ownership`() {
        DatabaseFixture.sqlite(directory.resolve("migration.db")).use { db ->
            db.migrate()
            val owner = UUID.randomUUID()
            val otherPlayer = UUID.randomUUID()
            db.seedPlayer(otherPlayer, 1, "other")
            db.seedPlayer(owner, 7)
            db.restore(DatabaseSnapshotDAO(
                listOf(PlayerDAO(1, owner, "owner")),
                listOf(ChestDAO(1, 1)),
                listOf(SlotDAO(0, 1, 1, byteArrayOf(42)))
            ))
            assertArrayEquals(byteArrayOf(42), db.slotBytes(), "The item must actually be restored")
            assertEquals(owner, db.slotOwner(), "Source player_id=1 belongs to target player_id=7, not the other player")
        }
    }

    @Test
    fun `saving and reading an ordinary chest preserves its item bytes`() {
        PluginTestScope().use {
            DatabaseFixture.sqlite(directory.resolve("control.db")).use { db ->
                db.migrate()
                val owner = UUID.randomUUID()
                db.seedPlayer(owner)
                db.save(owner, mapOf(3 to byteArrayOf(10, 20)))
                val slot = db.open(owner)!!.single()
                assertEquals(3, slot.slot)
                assertArrayEquals(byteArrayOf(10, 20), slot.itemBytes)
            }
        }
    }
}
