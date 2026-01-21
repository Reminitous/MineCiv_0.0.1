package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.registry.ModBlocks;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class MonumentPlacementValidationEvents {

    private MonumentPlacementValidationEvents() {}

    // OPTIONAL: spacing rule (uncomment usage below if you want it)
    // private static final int MIN_MONUMENT_DISTANCE_BLOCKS = 256; // 16 chunks

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

        // Rule: must be on solid ground
        if (!level.getBlockState(pos.below()).isSolid()) {
            e.setCanceled(true);
            player.sendSystemMessage(Component.literal("Monuments must be placed on solid ground."));
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

        // Rule: only one Monument per chunk (1.21.1-safe)
        LevelChunk chunk = level.getChunk(cp.x, cp.z);
        boolean alreadyHasMonument = false;

        for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
            BlockEntity be = chunk.getBlockEntity(bePos);
            if (be instanceof net.reminitous.mineciv.monument.MonumentBlockEntity) {
                alreadyHasMonument = true;
                break;
            }
        }

        if (alreadyHasMonument) {
            e.setCanceled(true);
            player.sendSystemMessage(Component.literal("There is already a Monument in this chunk."));
            return;
        }

        // OPTIONAL: minimum distance from any existing civ monument
        // if (isNearAnyMonument(level, pos, MIN_MONUMENT_DISTANCE_BLOCKS)) {
        //     e.setCanceled(true);
        //     player.sendSystemMessage(Component.literal("Too close to another civilization monument."));
        // }
    }

    // OPTIONAL helper for spacing rule
    @SuppressWarnings("unused")
    private static boolean isNearAnyMonument(ServerLevel level, BlockPos placedPos, int minDistanceBlocks) {
        CivSavedData data = CivSavedData.get(level.getServer());
        long minDistSq = (long) minDistanceBlocks * (long) minDistanceBlocks;

        for (Civilization civ : data.civs().values()) {
            BlockPos m = civ.monumentPos();
            if (m == null) continue;

            String dim = civ.monumentDimId();
            if (dim == null) continue;
            if (!dim.equals(Level.OVERWORLD.location().toString())) continue;

            long dx = (long) m.getX() - placedPos.getX();
            long dz = (long) m.getZ() - placedPos.getZ();
            long distSq = dx * dx + dz * dz;

            if (distSq < minDistSq) return true;
        }

        return false;
    }
}
