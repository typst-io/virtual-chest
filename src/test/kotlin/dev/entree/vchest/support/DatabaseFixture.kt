package dev.entree.vchest.support

import dev.entree.vchest.dbms.DatabaseSnapshotDAO
import dev.entree.vchest.dbms.MySQLQueries
import dev.entree.vchest.dbms.SQLiteQueries
import dev.entree.vchest.dbms.SlotDAO
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.io.Closeable
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.*

class DatabaseFixture(val connection: Connection, val dialect: SQLDialect) : Closeable {
    val dsl = DSL.using(connection, dialect)

    companion object {
        fun sqlite(path: Path): DatabaseFixture = DatabaseFixture(
            DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}"), SQLDialect.SQLITE
        ).apply { dsl.execute("PRAGMA foreign_keys = ON") }
    }

    fun migrate() {
        val protocol = if (dialect == SQLDialect.SQLITE) "sqlite" else "mysql"
        for (migration in listOf("V1__init.sql", "V2__lock.sql")) {
            val sql = checkNotNull(javaClass.getResourceAsStream("/db/migration/$protocol/$migration"))
                .bufferedReader().use { it.readText() }
            sql.split(';').map(String::trim).filter(String::isNotEmpty).forEach(dsl::execute)
        }
    }

    fun seedPlayer(uuid: UUID, id: Int = 1, name: String = "owner") {
        dsl.execute("INSERT INTO player(player_id, player_uuid, player_name) VALUES (?, ?, ?)", id, uuid.toString(), name)
    }

    fun save(uuid: UUID, items: Map<Int, ByteArray>, num: Int = 1): IntArray =
        if (dialect == SQLDialect.SQLITE) SQLiteQueries.upsertChest(dsl, uuid, num, items)
        else MySQLQueries.upsertChest(dsl, uuid, num, items)

    fun open(uuid: UUID, num: Int = 1): List<SlotDAO>? =
        if (dialect == SQLDialect.SQLITE) SQLiteQueries.popChest(dsl, uuid, num)
        else MySQLQueries.popChest(dsl, uuid, num)

    fun expireLease() {
        // Move the DB lease into the past without sleeping or changing the machine's clock.
        dsl.execute("UPDATE chest SET chest_expires_at = '2000-01-01 00:00:00'")
    }

    fun restore(snapshot: DatabaseSnapshotDAO): Int =
        if (dialect == SQLDialect.SQLITE) SQLiteQueries.setWholeSnapshot(dsl, snapshot)
        else MySQLQueries.setWholeSnapshot(dsl, snapshot)

    fun slotOwner(): UUID = UUID.fromString(dsl.fetchOne(
        "SELECT p.player_uuid FROM slot s JOIN player p ON p.player_id = s.slot_player_id"
    )!!.get(0, String::class.java))

    fun slotBytes(): ByteArray? = dsl.fetchOne("SELECT slot_item_bytes FROM slot")?.get(0, ByteArray::class.java)

    override fun close() = connection.close()
}
