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

        // Rule: Overworld only
        if (level.dimension() != Level.OVERWORLD) {
            e.setCanceled(true);
            player.sendSystemMessage(Component.literal("Monuments can only be placed in the Overworld."));
            return;
        }

        CivSavedData data = CivSavedData.get(level.getServer());

        // Rule: must NOT already be in a civ
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
        // IMPORTANT: ignore the monument BE at the position we're currently placing
        var chunk = level.getChunkAt(pos);
        boolean alreadyHasOtherMonument = chunk.getBlockEntities().entrySet().stream()
                .anyMatch(entry ->
                        !entry.getKey().equals(pos) &&
                                entry.getValue() instanceof net.reminitous.mineciv.monument.MonumentBlockEntity
                );

        if (alreadyHasOtherMonument) {
            e.setCanceled(true);
            player.sendSystemMessage(Component.literal("There is already a Monument in this chunk."));
        }
    }
}
