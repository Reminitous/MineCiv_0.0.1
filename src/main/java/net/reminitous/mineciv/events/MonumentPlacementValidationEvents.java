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

    // ---- Tuning knobs (in CHUNKS) ----
    public static final int MIN_CHUNKS_FROM_SPAWN = 25;
    public static final int MIN_CHUNKS_BETWEEN_MONUMENTS = 25;

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

        // Rule: cannot place within MIN_CHUNKS_FROM_SPAWN chunks of world spawn (distance in chunks)
        BlockPos spawn = level.getSharedSpawnPos();
        ChunkPos spawnChunk = new ChunkPos(spawn);
        int dxSpawn = Math.abs(cp.x - spawnChunk.x);
        int dzSpawn = Math.abs(cp.z - spawnChunk.z);
        int chebSpawn = Math.max(dxSpawn, dzSpawn); // chunk-square distance

        if (chebSpawn < MIN_CHUNKS_FROM_SPAWN) {
            e.setCanceled(true);
            player.sendSystemMessage(Component.literal(
                    "Monuments must be at least " + MIN_CHUNKS_FROM_SPAWN + " chunks from spawn."
            ));
            return;
        }

        // Rule: cannot place within MIN_CHUNKS_BETWEEN_MONUMENTS chunks of another civ's monument (distance in chunks)
        for (var civ : data.civs().values()) {
            BlockPos mPos = civ.monumentPos();
            if (mPos == null) continue;

            // Only compare monuments in the overworld (your current design)
            String dim = civ.monumentDimId();
            if (dim == null) continue;
            if (!dim.equals(Level.OVERWORLD.location().toString())) continue;

            ChunkPos other = new ChunkPos(mPos);

            int dx = Math.abs(cp.x - other.x);
            int dz = Math.abs(cp.z - other.z);
            int cheb = Math.max(dx, dz);

            if (cheb < MIN_CHUNKS_BETWEEN_MONUMENTS) {
                e.setCanceled(true);
                player.sendSystemMessage(Component.literal(
                        "Monuments must be at least " + MIN_CHUNKS_BETWEEN_MONUMENTS + " chunks from other monuments."
                ));
                return;
            }
        }

        // Rule: only one Monument per chunk (block entity scan)
        var chunk = level.getChunkAt(pos);
        boolean alreadyHasMonument = chunk.getBlockEntitiesPos().stream()
                .anyMatch(bePos -> chunk.getBlockEntity(bePos) instanceof net.reminitous.mineciv.monument.MonumentBlockEntity);

        if (alreadyHasMonument) {
            e.setCanceled(true);
            player.sendSystemMessage(Component.literal("There is already a Monument in this chunk."));
        }
    }
}
