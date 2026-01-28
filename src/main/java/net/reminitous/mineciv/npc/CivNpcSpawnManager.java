package net.reminitous.mineciv.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.registry.ModEntities;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CivNpcSpawnManager {

    private CivNpcSpawnManager() {}

    /* ---------------- Entry points ---------------- */

    public static void maintainAllCivs(ServerLevel overworldAuthority) {
        MinecraftServer server = overworldAuthority.getServer();
        CivSavedData data = CivSavedData.get(server);

        for (Civilization civ : new ArrayList<>(data.civs().values())) {
            if (civ != null) {
                maintainOneCiv(server, data, civ);
            }
        }
    }

    public static void maintainOneCiv(MinecraftServer server, CivSavedData data, Civilization civ) {
        if (server == null || data == null || civ == null) return;

        BlockPos monument = civ.monumentPos();
        if (monument == null) return;

        // For now: authority dimension is overworld (you can later use civ.monumentDimId())
        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null || !level.hasChunkAt(monument)) return;

        // Make sure desired counts exist and are legal
        civ.ensureDefaultDesiredIfEmpty();
        civ.clampDesiredToCapAndClass();

        // 1) Remove dead/invalid UUIDs
        cleanupNpcIds(server, civ);

        // 2) Count existing NPCs by role
        Map<NpcRoleType, Integer> existing = countExistingByRole(server, civ);

        // 3) Determine desired counts (already clamped by civ class + cap)
        Map<NpcRoleType, Integer> desired = new EnumMap<>(NpcRoleType.class);
        for (NpcRoleType role : NpcRoleType.values()) {
            desired.put(role, civ.desiredCount(role));
        }

        // 4) Spawn missing
        for (NpcRoleType role : NpcRoleType.values()) {
            int have = existing.getOrDefault(role, 0);
            int want = desired.getOrDefault(role, 0);
            int need = Math.max(0, want - have);
            for (int i = 0; i < need; i++) {
                spawnOne(level, civ, role, monument);
            }
        }

        data.putCiv(civ);
    }

    /* ---------------- Spawning ---------------- */

    private static void spawnOne(ServerLevel level, Civilization civ, NpcRoleType role, BlockPos monument) {
        if (level == null || civ == null || role == null || monument == null) return;

        Mob mob = createMobForRole(level, role);
        if (mob == null) return;

        BlockPos pos = findSpawnPosNear(level, monument, 6, level.random);
        if (pos == null) return;

        mob.moveTo(
                pos.getX() + 0.5,
                pos.getY(),
                pos.getZ() + 0.5,
                level.random.nextFloat() * 360F,
                0F
        );

        if (mob instanceof MineCivNpcBase base) {
            base.bindToCiv(civ.id(), monument);
            base.setRole(role.name());
        } else {
            // Failsafe: still store on persistent data if you ever spawn a non-base entity by mistake
            mob.getPersistentData().putUUID("MineCivCivId", civ.id());
            mob.getPersistentData().putString("MineCivRole", role.name());
        }

        level.addFreshEntity(mob);
        civ.addNpcId(mob.getUUID());
    }

    private static Mob createMobForRole(ServerLevel level, NpcRoleType role) {
        return switch (role) {
            case FARMER -> ModEntities.NPC_FARMER.get().create(level);
            case SHEPHERD -> ModEntities.NPC_SHEPHERD.get().create(level);
            case LUMBERJACK -> ModEntities.NPC_LUMBERJACK.get().create(level);

            case PATROL -> ModEntities.NPC_PATROL.get().create(level);
            case KNIGHT -> ModEntities.NPC_KNIGHT.get().create(level);
            case ARCHER -> ModEntities.NPC_ARCHER.get().create(level);

            case WORKER -> ModEntities.NPC_WORKER.get().create(level);
            case MINER -> ModEntities.NPC_MINER.get().create(level);

            case WITCH -> ModEntities.NPC_WITCH.get().create(level);
            case WIZARD -> ModEntities.NPC_WIZARD.get().create(level);
            case ENCHANTER -> ModEntities.NPC_ENCHANTER.get().create(level);
        };
    }

    /* ---------------- Spawn positioning ---------------- */

    private static BlockPos findSpawnPosNear(
            ServerLevel level,
            BlockPos center,
            int radius,
            RandomSource rand
    ) {
        for (int i = 0; i < 20; i++) {
            int dx = rand.nextIntBetweenInclusive(-radius, radius);
            int dz = rand.nextIntBetweenInclusive(-radius, radius);

            BlockPos top = level.getHeightmapPos(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    center.offset(dx, 0, dz)
            );

            if (!level.getBlockState(top.below()).isSolid()) continue;
            if (!level.getBlockState(top).getCollisionShape(level, top).isEmpty()) continue;
            if (!level.getBlockState(top.above()).getCollisionShape(level, top.above()).isEmpty()) continue;
            if (top.closerThan(center, 2.0)) continue;

            return top;
        }

        return null;
    }

    /* ---------------- Counting & cleanup ---------------- */

    private static void cleanupNpcIds(MinecraftServer server, Civilization civ) {
        List<UUID> ids = new ArrayList<>(civ.npcIds());

        for (UUID id : ids) {
            if (id == null) continue;

            boolean found = false;
            for (ServerLevel dim : server.getAllLevels()) {
                if (dim.getEntity(id) != null) {
                    found = true;
                    break;
                }
            }

            if (!found) civ.removeNpcId(id);
        }
    }

    private static Map<NpcRoleType, Integer> countExistingByRole(MinecraftServer server, Civilization civ) {
        EnumMap<NpcRoleType, Integer> counts = new EnumMap<>(NpcRoleType.class);
        for (NpcRoleType r : NpcRoleType.values()) counts.put(r, 0);

        for (UUID id : civ.npcIds()) {
            if (id == null) continue;

            for (ServerLevel dim : server.getAllLevels()) {
                var e = dim.getEntity(id);
                if (e == null) continue;

                if (e instanceof MineCivFarmerNpc) inc(counts, NpcRoleType.FARMER);
                else if (e instanceof MineCivShepherdNpc) inc(counts, NpcRoleType.SHEPHERD);
                else if (e instanceof MineCivLumberjackNpc) inc(counts, NpcRoleType.LUMBERJACK);

                else if (e instanceof MineCivPatrolNpc) inc(counts, NpcRoleType.PATROL);
                else if (e instanceof MineCivKnightNpc) inc(counts, NpcRoleType.KNIGHT);
                else if (e instanceof MineCivArcherNpc) inc(counts, NpcRoleType.ARCHER);

                else if (e instanceof MineCivWorkerNpc) inc(counts, NpcRoleType.WORKER);
                else if (e instanceof MineCivMinerNpc) inc(counts, NpcRoleType.MINER);

                else if (e instanceof MineCivWitchNpc) inc(counts, NpcRoleType.WITCH);
                else if (e instanceof MineCivWizardNpc) inc(counts, NpcRoleType.WIZARD);
                else if (e instanceof MineCivEnchanterNpc) inc(counts, NpcRoleType.ENCHANTER);

                // found entity for this UUID; stop scanning dims
                break;
            }
        }

        return counts;
    }

    private static void inc(EnumMap<NpcRoleType, Integer> counts, NpcRoleType role) {
        counts.put(role, counts.getOrDefault(role, 0) + 1);
    }
}
