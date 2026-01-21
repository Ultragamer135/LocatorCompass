package io.github.ultragamer135.locator_compass

import net.fabricmc.fabric.api.item.v1.FabricItemSettings
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupEntries
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents.ModifyEntries
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemGroups
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.nbt.NbtHelper
import net.minecraft.registry.Registries
import net.minecraft.registry.Registry
import net.minecraft.server.network.ServerPlayerEntity
import net.minecraft.text.Text
import net.minecraft.util.Hand
import net.minecraft.util.Identifier
import net.minecraft.util.TypedActionResult
import net.minecraft.world.World
import java.util.*

object ModItems {
    private val LOCATOR_COMPASS: Item? = Registry.register(Registries.ITEM, Identifier("locator_compass", "locator_compass"), TrackingCompass(FabricItemSettings().maxCount(1)))

    @JvmStatic
    fun initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(ModifyEntries { content: FabricItemGroupEntries ->
            content.add(LOCATOR_COMPASS)
        })
    }

    class TrackingCompass(settings: Settings) : Item(settings) {
        private var lastTargetUuid: UUID? = null

        override fun use(world: World, user: PlayerEntity, hand: Hand): TypedActionResult<ItemStack> {
            val stack = user.getStackInHand(hand)
            if (!world.isClient) {
                // 1. Get a list of valid target players
                val targets: MutableList<ServerPlayerEntity> = ArrayList(
                    world.server!!.playerManager.playerList.stream() // Get a list of players and filter it:
                        .filter { p: ServerPlayerEntity -> p.uuid != user.uuid }  // Isn't the player using the item
                        .filter { p: ServerPlayerEntity? -> !user.isTeammate(p) }  // Isn't a teammate
                        .filter { p: ServerPlayerEntity -> !p.hasStatusEffect(StatusEffects.INVISIBILITY) }  // Doesn't have invisibility
                        .toList())

                if (targets.size > 1) { // Remove the last targeted player, if there is more than one valid target
                    targets.removeIf { p: ServerPlayerEntity -> p.uuid == lastTargetUuid }
                }

                if (!targets.isEmpty()) {
                    // 2. Choose a random target
                    val target = targets[world.random.nextInt(targets.size)]
                    lastTargetUuid = target.uuid

                    // 3. Create a Vanilla Compass stack
                    val vanillaCompass = ItemStack(Items.COMPASS)
                    val nbt = vanillaCompass.getOrCreateNbt()

                    // 4. Set the standard Vanilla NBT tags
                    nbt.put("LodestonePos", NbtHelper.fromBlockPos(target.blockPos))
                    nbt.putString("LodestoneDimension", target.world.registryKey.value.toString())
                    nbt.putBoolean("LodestoneTracked", false) // Very important!

                    // Optional: Give it a custom name so they know who they're tracking
                    vanillaCompass.setCustomName(Text.literal("Tracking: " + target.entityName))

                    // 5. Return the new compass in place of the original item
                    return TypedActionResult.success(vanillaCompass)
                } else {
                    user.sendMessage(Text.literal("No valid players found!"), true)
                }
            }
            return TypedActionResult.consume(stack)
        }
    }
}
