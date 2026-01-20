package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.registry.ModBlocks;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class MonumentPlacementValidationEvents {

    private MonumentPlacementValidationEvents() {}

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        // Only care about our monument block
        if (e.getPlacedBlock().getBlock() != ModBlocks.MONUMENT.get()) return;

        BlockPos pos = e.getPos();

        // Rule: Overworld only (change/remove if you want multi-dim)
        if (level.dimension() != Level.OVERWORLD) {
            e.setCanceled(true);
            player.sendSystemMessage(Component.literal("Monuments can only be placed in the Overworld."));
            return;
        }

        // Rule: must NOT already be in a civ
        CivSavedData data = CivSavedData.get(level.getServer());
        UUID myCiv = data.getPlayersCiv(player.getUUID());
        if (myCiv != null) {
            e.setCanceled(true);
            player.sendSystemMessage(Component.literal("You are already in a civilization."));
            return;
        }

        // Rule: chunk must be unclaimed
        ChunkPos cp = new ChunkPos(pos);
        UUID owner = data.getChunkOwner(cp.toLong());
        if (owner != null) {
            e.setCanceled(true);
            player.sendSystemMessage(Component.literal("You cannot place a Monument in claimed territory."));
            return;
        }

        // Rule: only one Monument per chunk
        var chunk = level.getChunkAt(pos);
        boolean alreadyHasMonument = chunk.getBlockEntities().values().stream()
                .anyMatch(be -> be instanceof net.reminitous.mineciv.monument.MonumentBlockEntity);

        if (alreadyHasMonument) {
            e.setCanceled(true);
            player.sendSystemMessage(Component.literal("There is already a Monument in this chunk."));
        }
    }
}
