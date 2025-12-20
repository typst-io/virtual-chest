package dev.entree.vchest.cmd

import dev.entree.vchest.*
import dev.entree.vchest.api.ChestOpenEvent
import dev.entree.vchest.config.PluginConfig
import dev.entree.vchest.dbms.ChestRepository
import dev.entree.vchest.dbms.JDBCChestRepository
import dev.entree.vchest.inventory.InventoryEngine
import io.typst.command.Argument
import io.typst.command.Command
import io.typst.command.CommandCancellationException
import io.typst.command.StandardArguments.intArg
import io.typst.command.StandardArguments.strArg
import io.typst.command.bukkit.BukkitArguments.playerArg
import io.typst.command.bukkit.BukkitCommandConfig
import io.typst.command.bukkit.BukkitCommandHelp
import io.typst.command.bukkit.BukkitCommands
import io.typst.command.bukkit.BukkitControlFlows.getPlayerOrThrow
import io.typst.command.kotlin.command
import io.typst.command.kotlin.commandMap
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.java.JavaPlugin
import org.jooq.SQLDialect
import java.util.*
import java.util.concurrent.CompletableFuture

sealed interface ChestCommand {
    data class OpenChest(val numO: Optional<Int>) : ChestCommand
    data class OpenChestOthers(val player: Player, val num: Int) : ChestCommand
    data class DataMigration(val dataType: String) : ChestCommand

    companion object {
        val dataSourceTypeArg: Argument<String> = strArg.withName("type").withTabCompletes {
            listOf("sqlite", "mysql")
        }
        val node: Command<ChestCommand> = commandMap(
            "open" to command(::OpenChestOthers, playerArg, intArg.withName("num"))
                .withPermission(ChestPlugin.perm),
            "migration" to command(::DataMigration, dataSourceTypeArg).withPermission(ChestPlugin.perm)
        ).withFallback(command(::OpenChest, intArg.withName("num").asOptional()))
        val nodeKr: Command<ChestCommand> = commandMap(
            "열기" to command(::OpenChestOthers, playerArg, intArg.withName("번호"))
                .withPermission(ChestPlugin.perm),
            "데이터이전" to command(::DataMigration, dataSourceTypeArg).withPermission(ChestPlugin.perm)
        ).withFallback(command(::OpenChest, intArg.withName("번호").asOptional()))

        fun getDialectOrThrow(xs: String): SQLDialect {
            return if (xs == "sqlite") {
                SQLDialect.SQLITE
            } else if (xs == "mysql") {
                SQLDialect.MYSQL
            } else throw CommandCancellationException("§cUnknown database type. Available: sqlite, mysql")
        }

        fun register(plugin: JavaPlugin, config: PluginConfig = chestPlugin.pluginConfig) {
            val cmdConfig = BukkitCommandConfig.empty.withFormatter { help ->
                val newHelp = if (chestPlugin.pluginConfig.overrideLocale.isNotEmpty()) {
                    help.withLanguage(chestPlugin.pluginConfig.overrideLocale)
                } else {
                    help
                }
                BukkitCommandHelp.format(newHelp)
            }
            BukkitCommands.registerPrime("vc", node, ::execute, { _, _ -> emptyList<String>() }, cmdConfig, plugin)
            BukkitCommands.registerPrime("창고", nodeKr, ::execute, { _, _ -> emptyList<String>() }, cmdConfig, plugin)
        }

        fun saveChest(player: Player, num: Int, items: Map<Int, ItemStack>, repository: ChestRepository): CompletableFuture<IntArray> {
            return chestPlugin.futureTaskAsync {
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
            }.thenApplySync { results ->
                if (results.isEmpty() && player.isOnline) {
                    for ((_, item) in items) {
                        player.giveItemOrDrop(item)
                    }
                }
                results
            }
        }

        fun openChest(ctx: OpenChestContext): CompletableFuture<Boolean> {
            val (viewer, num, target, title) = ctx
            if (ctx.isOwnerPlayer) {
                val openEvent = ChestOpenEvent(num, viewer)
                Bukkit.getPluginManager().callEvent(openEvent)
                if (openEvent.isCancelled) {
                    return CompletableFuture.completedFuture(false)
                }
            }
            val repository = chestPlugin.repository
            val uuid = target.uniqueId
            val prevCtx = chestPlugin.openingChests[uuid]
            if (prevCtx != null && viewer.hasPermission(ChestPlugin.perm)) {
                Bukkit.getPlayer(prevCtx.viewer)?.closeInventory()
            }
            return repository.fetchChest(uuid, num).thenApplySync { records ->
                if (records == null) {
                    viewer.sendMessage(chestPlugin.pluginConfig.alreadyOpenedChestMessage.colorize())
                    return@thenApplySync false
                }
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
                val title = title.ifEmpty { config.chestTitle.replace("%num%", num.toString()).colorize() }
                val row = chestPlugin.pluginConfig.chestSizeRow.coerceIn(1, 6)
                val (coerceItems, otherItems) = InventoryEngine.coerceRow(items, row)
                val inv = InventoryEngine.createChest(title, row, coerceItems) { newItems ->
                    chestPlugin.openingChests.remove(uuid)
                    saveChest(target, num, newItems, repository).thenAcceptSyncPrime {
                        ctx.onClose?.invoke(it)
                    }
                }
                viewer.openInventory(inv)
                otherItems.forEach(viewer::giveItemOrDrop)
                chestPlugin.openingChests[uuid] = ChestViewContext(uuid, num, viewer.uniqueId)
                true
            }
        }

        @Deprecated("Use openChest(OpenChestContext) instead.")
        fun openChest(viewer: Player, target: Player, num: Int, repository: ChestRepository) {
            openChest(OpenChestContext(viewer, num, target)).thenAcceptSync {
                // nothing
            }
        }

        /**
         * Fires [ChestOpenEvent] event.
         */
        @Deprecated("Use openChest(OpenChestContext) instead.")
        fun openChest(player: Player, num: Int, repository: ChestRepository): Boolean {
            val ret = openChest(OpenChestContext(player, num))
            return if (ret.isDone) {
                ret.get()
            } else true
        }

        fun execute(sender: CommandSender, x: ChestCommand) {
            when (x) {
                is OpenChest -> {
                    val num = x.numO.orElse(1)
                    val player = getPlayerOrThrow(sender)
                    val perm = "virtualchest.chest.${num}"
                    if (!player.hasPermission(perm)) {
                        throw CommandCancellationException(
                            chestPlugin.pluginConfig.noPermissionMessage.replace(
                                "%perm%",
                                perm
                            ).colorize()
                        )
                    }
                    val ctx = OpenChestContext(player, num)
                    openChest(ctx).thenAcceptSync {
                        if (!it) {
                            sender.sendMessage("§cCancelled by the system.")
                        }
                    }
                }

                is DataMigration -> {
                    getDialectOrThrow(x.dataType)
                    if (chestPlugin.pluginConfig.dbProtocol == x.dataType) {
                        throw CommandCancellationException("§cCannot migration from the same db type: ${x.dataType}")
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
                        sender.sendMessage("migration done!")
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
