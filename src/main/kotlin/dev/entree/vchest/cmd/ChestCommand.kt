package dev.entree.vchest.cmd

import dev.entree.vchest.*
import dev.entree.vchest.api.ChestOpenEvent
import dev.entree.vchest.dbms.ChestRepository
import dev.entree.vchest.inventory.InventoryEngine
import io.typst.command.Command
import io.typst.command.CommandCancellationException
import io.typst.command.StandardArguments.intArg
import io.typst.command.bukkit.BukkitControlFlows.getPlayerOrThrow
import io.typst.command.kotlin.command
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

sealed interface ChestCommand {
    data class OpenChest(val num: Int) : ChestCommand

    companion object {
        val node: Command<ChestCommand> = command(::OpenChest, intArg.withName("번호"))

        fun saveChest(player: Player, num: Int, items: Map<Int, ItemStack>, repository: ChestRepository) {
            if (items.isEmpty()) {
                return
            }
            chestPlugin.futureTask {
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

        fun openChest(player: Player, num: Int, repository: ChestRepository): Boolean {
            val uuid = player.uniqueId
            val openEvent = ChestOpenEvent(num, player)
            Bukkit.getPluginManager().callEvent(openEvent)
            if (openEvent.isCancelled) {
                return false
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
                val inv = InventoryEngine.createChest(title, items) { newItems ->
                    chestPlugin.openingChests.remove(uuid)
                    saveChest(player, num, newItems, repository)
                }
                player.openInventory(inv)
                chestPlugin.openingChests[uuid] = ChestViewContext(uuid, num)
            }
            return true
        }

        fun execute(sender: CommandSender, x: ChestCommand) {
            when (x) {
                is OpenChest -> {
                    val player = getPlayerOrThrow(sender)
                    val perm = "virtualchest.chest.${x.num}"
                    if (!player.hasPermission(perm) || !openChest(player, x.num, chestPlugin.repository)) {
                        throw CommandCancellationException("§c권한이 없습니다: $perm")
                    }
                }
            }
        }
    }
}
