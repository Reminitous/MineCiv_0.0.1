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

        ServerLevel level = server.getLevel(Level.OVERWORLD);
        if (level == null || !level.hasChunkAt(monument)) return;

        cleanupNpcIds(server, civ);

        Map<NpcRoleType, Integer> existing = countExistingByRole(server, civ);

        int civLevel = civ.level();

        for (NpcRoleType role : NpcRoleType.values()) {
            int allowed = maxAllowedForRole(role, civLevel);
            int have = existing.getOrDefault(role, 0);

            int need = Math.max(0, allowed - have);
            for (int i = 0; i < need; i++) {
                spawnOne(level, civ, role, monument);
            }
        }

        data.putCiv(civ);
    }

    /* ---------------- Spawn rules ---------------- */

    private static int maxAllowedForRole(NpcRoleType role, int civLevel) {
        return switch (role) {

            // Level 1
            case WORKER -> civLevel >= 1 ? 2 : 0;

            // Level 2
            case FARMER, LUMBERJACK, SHEPHERD ->
                    civLevel >= 2 ? 2 : 0;

            // Level 3
            case PATROL ->
                    civLevel >= 3 ? 2 : 0;

            // Level 4
            case KNIGHT, ARCHER ->
                    civLevel >= 4 ? 2 : 0;

            // Level 5+
            case WITCH, WIZARD, ENCHANTER ->
                    civLevel >= 5 ? 1 : 0;

            // Safety default
            default -> 0;
        };
    }

    /* ---------------- Spawning ---------------- */

    private static void spawnOne(ServerLevel level, Civilization civ, NpcRoleType role, BlockPos monument) {
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

    private static BlockPos findSpawnPosNear(ServerLevel level, BlockPos center, int radius, RandomSource rand) {
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

    /* ---------------- Cleanup & counting ---------------- */

    private static void cleanupNpcIds(MinecraftServer server, Civilization civ) {
        List<UUID> ids = new ArrayList<>(civ.npcIds());

        for (UUID id : ids) {
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

                break;
            }
        }
        return counts;
    }

    private static void inc(EnumMap<NpcRoleType, Integer> counts, NpcRoleType role) {
        counts.put(role, counts.get(role) + 1);
    }
}
