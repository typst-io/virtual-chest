package dev.entree.vchest.cmd

import dev.entree.vchest.*
import dev.entree.vchest.api.ChestOpenEvent
import dev.entree.vchest.dbms.ChestRepository
import dev.entree.vchest.dbms.JDBCChestRepository
import dev.entree.vchest.inventory.InventoryEngine
import io.typst.command.Argument
import io.typst.command.Command
import io.typst.command.CommandCancellationException
import io.typst.command.StandardArguments.intArg
import io.typst.command.StandardArguments.strArg
import io.typst.command.bukkit.BukkitArguments.playerArg
import io.typst.command.bukkit.BukkitControlFlows.getPlayerOrThrow
import io.typst.command.kotlin.command
import io.typst.command.kotlin.commandMap
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.jooq.SQLDialect
import java.util.*

sealed interface ChestCommand {
    data class OpenChest(val numO: Optional<Int>) : ChestCommand
    data class OpenChestOthers(val player: Player, val num: Int) : ChestCommand
    data class DataMigration(val dataType: String) : ChestCommand

    companion object {
        val dataSourceTypeArg: Argument<String> = strArg.withName("type").withTabCompletes {
            listOf("sqlite", "mysql")
        }
        val node: Command<ChestCommand> = commandMap(
            "열기" to command(::OpenChestOthers, playerArg, intArg.withName("num"))
                .withPermission(ChestPlugin.perm),
            "데이터이전" to command(::DataMigration, dataSourceTypeArg).withPermission(ChestPlugin.perm)
        ).withFallback(command(::OpenChest, intArg.withName("num").asOptional()))

        fun getDialectOrThrow(xs: String): SQLDialect {
            return if (xs == "sqlite") {
                SQLDialect.SQLITE
            } else if (xs == "mysql") {
                SQLDialect.MYSQL
            } else throw CommandCancellationException("§c존재하지 않는 데이터 타입입니다. 가능한 데이터 타입: sqlite, mysql")
        }

        fun saveChest(player: Player, num: Int, items: Map<Int, ItemStack>, repository: ChestRepository) {
            if (items.isEmpty()) {
                return
            }
            chestPlugin.futureTaskAsync {
                items.flatMap {
                    val bytes = runCatching {
                        it.value.serializeAsBytes()
                    }.getOrNull()
                    if (bytes != null) {
                        listOf(it.key to bytes)
                    } else emptyList()
                }.toMap()
            }.flatMap { serializedItems ->
                repository.upsertChest(player.uniqueId, num, serializedItems)
            }.thenAcceptSync { results ->
                if (results.isEmpty() && player.isOnline) {
                    for ((_, item) in items) {
                        player.giveItemOrDrop(item)
                    }
                }
            }
        }

        fun openChest(viewer: Player, target: Player, num: Int, repository: ChestRepository) {
            val uuid = target.uniqueId
            val prevCtx = chestPlugin.openingChests[uuid]
            if (prevCtx != null && viewer.hasPermission(ChestPlugin.perm)) {
                Bukkit.getPlayer(prevCtx.viewer)?.closeInventory()
            }
            repository.popChest(uuid, num).thenAcceptSync { records ->
                val items = records.flatMap {
                    val itemBytes = it.itemBytes
                    val item = if (itemBytes.isNotEmpty()) {
                        runCatching {
                            ItemStack.deserializeBytes(it.itemBytes)
                        }.getOrNull()
                    } else null
                    if (item != null) {
                        listOf(it.slot to item)
                    } else emptyList()
                }.toMap()
                val config = chestPlugin.pluginConfig
                val title = config.chestTitle.replace("%num%", num.toString())
                val row = chestPlugin.pluginConfig.chestSizeRow.coerceIn(1, 6)
                val (coerceItems, otherItems) = InventoryEngine.coerceRow(items, row)
                val inv = InventoryEngine.createChest(title, row, coerceItems) { newItems ->
                    chestPlugin.openingChests.remove(uuid)
                    saveChest(target, num, newItems, repository)
                }
                viewer.openInventory(inv)
                otherItems.forEach(viewer::giveItemOrDrop)
                chestPlugin.openingChests[uuid] = ChestViewContext(uuid, num, viewer.uniqueId)
            }
        }

        /**
         * Fires [ChestOpenEvent] event.
         */
        fun openChest(player: Player, num: Int, repository: ChestRepository): Boolean {
            val openEvent = ChestOpenEvent(num, player)
            Bukkit.getPluginManager().callEvent(openEvent)
            if (openEvent.isCancelled) {
                return false
            }
            openChest(player, player, num, repository)
            return true
        }

        fun execute(sender: CommandSender, x: ChestCommand) {
            when (x) {
                is OpenChest -> {
                    val num = x.numO.orElse(1)
                    val player = getPlayerOrThrow(sender)
                    val perm = "virtualchest.chest.${num}"
                    if (!player.hasPermission(perm) || !openChest(player, num, chestPlugin.repository)) {
                        throw CommandCancellationException("§c권한이 없습니다: $perm")
                    }
                }

                is DataMigration -> {
                    getDialectOrThrow(x.dataType)
                    if (chestPlugin.pluginConfig.dbProtocol == x.dataType) {
                        throw CommandCancellationException("§c동일한 데이터베이스로 이전할 수 없습니다: ${x.dataType}")
                    }
                    val jdbcCtx = chestPlugin.getJDBCContext(chestPlugin.pluginConfig.copy(dbProtocol = x.dataType))
                    val snapshotFuture = chestPlugin.repository.getWholeSnapshot()
                    chestPlugin.futureTaskAsync {
                        val snapshot = snapshotFuture.join()
                        JDBCChestRepository.create(chestPlugin, jdbcCtx).use { tempRepo ->
                            tempRepo.setWholeSnapshot(snapshot)
                        }
                    }.thenAcceptSync {
                        chestPlugin.logger.info("migration done!")
                        sender.sendMessage("이전 완료")
                    }
                }

                is OpenChestOthers -> {
                    val player = getPlayerOrThrow(sender)
                    openChest(player, x.player, x.num, chestPlugin.repository)
                }
            }
        }
    }
}
