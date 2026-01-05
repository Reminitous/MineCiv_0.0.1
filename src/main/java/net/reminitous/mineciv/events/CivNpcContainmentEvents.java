package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class CivNpcContainmentEvents {

    private CivNpcContainmentEvents() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.level instanceof ServerLevel level)) return;

        // Every 5 seconds
        if ((level.getServer().getTickCount() % 100) != 0) return;

        CivSavedData data = CivSavedData.get(level.getServer());

        for (Civilization civ : data.civs().values()) {
            BlockPos monument = civ.monumentPos();
            if (monument == null) continue;

            String dim = civ.monumentDimId();
            if (dim == null || !dim.equals(level.dimension().location().toString())) continue;

            if (!level.hasChunkAt(monument)) continue;

            List<UUID> toRemove = new ArrayList<>();

            for (UUID npcId : civ.npcIds()) {
                Entity ent = level.getEntity(npcId);
                if (!(ent instanceof LivingEntity le) || !le.isAlive()) {
                    toRemove.add(npcId);
                    continue;
                }

                ChunkPos npcChunk = new ChunkPos(le.blockPosition());
                long npcChunkLong = npcChunk.toLong();

                if (!civ.claimedChunks().contains(npcChunkLong)) {
                    teleportBack(level, le, monument);
                }
            }

            if (!toRemove.isEmpty()) {
                for (UUID id : toRemove) civ.removeNpcId(id);
                data.putCiv(civ);
            }
        }
    }

    private static void teleportBack(ServerLevel level, LivingEntity le, BlockPos monument) {
        for (int i = 0; i < 12; i++) {
            int dx = level.random.nextInt(9) - 4;
            int dz = level.random.nextInt(9) - 4;

            BlockPos probe = monument.offset(dx, 0, dz);
            if (!level.hasChunkAt(probe)) continue;

            BlockPos ground = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    probe
            );

            if (!level.getBlockState(ground).isAir()) continue;
            if (!level.getBlockState(ground.above()).isAir()) continue;

            if (le instanceof net.minecraft.world.entity.Mob mob) {
                mob.getNavigation().stop();
            }

            le.teleportTo(
                    ground.getX() + 0.5,
                    ground.getY(),
                    ground.getZ() + 0.5
            );
            return;
        }

        BlockPos ground = level.getHeightmapPos(
                net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                monument
        );

        if (le instanceof net.minecraft.world.entity.Mob mob) {
            mob.getNavigation().stop();
        }

        le.teleportTo(
                ground.getX() + 0.5,
                ground.getY(),
                ground.getZ() + 0.5
        );
    }
}
