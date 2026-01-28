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

import java.util.*;

public final class CivNpcSpawnManager {

    private CivNpcSpawnManager() {}

    /** How often we try to keep NPCs at their target counts (called from tick events). */
    public static void maintainAllCivs(ServerLevel overworldAuthority) {
        MinecraftServer server = overworldAuthority.getServer();
        CivSavedData data = CivSavedData.get(server);

        for (Civilization civ : new ArrayList<>(data.civs().values())) {
            if (civ == null) continue;
            maintainOneCiv(server, data, civ);
        }
    }

    /** Ensure this civ has its target NPCs spawned & tracked. */
    public static void maintainOneCiv(MinecraftServer server, CivSavedData data, Civilization civ) {
        if (server == null || data == null || civ == null) return;

        BlockPos monument = civ.monumentPos();
        if (monument == null) return;

        ServerLevel level = server.getLevel(Level.OVERWORLD); // v1: monument authority in overworld
        if (level == null) return;

        // Do not force-load
        if (!level.hasChunkAt(monument)) return;

        // 1) Clean invalid/dead UUIDs
        cleanupNpcIds(server, civ);

        // 2) Count existing by role (by entity type)
        Counts existing = countExisting(server, civ);

        // 3) Target counts by civ level
        Counts target = targetCountsForLevel(civ.civLevel());

        int needFarmers = Math.max(0, target.farmers - existing.farmers);
        int needArchers = Math.max(0, target.archers - existing.archers);
        int needKnights = Math.max(0, target.knights - existing.knights);

        if (needFarmers == 0 && needArchers == 0 && needKnights == 0) return;

        // 4) Spawn missing near monument
        for (int i = 0; i < needFarmers; i++) {
            spawnOne(level, civ, Role.FARMER, monument);
        }
        for (int i = 0; i < needArchers; i++) {
            spawnOne(level, civ, Role.ARCHER, monument);
        }
        for (int i = 0; i < needKnights; i++) {
            spawnOne(level, civ, Role.KNIGHT, monument);
        }

        // Persist civ changes (npcIds)
        data.putCiv(civ);
    }

    /* ---------------- Targets ---------------- */

    /**
     * v1 progression table (change however you want):
     * Level 1: 1 Farmer
     * Level 2: 2 Farmers, 1 Archer
     * Level 3: 2 Farmers, 2 Archers, 1 Knight
     * Level 4+: 3 Farmers, 3 Archers, 2 Knights
     */
    private static Counts targetCountsForLevel(int lvl) {
        if (lvl <= 1) return new Counts(1, 0, 0);
        if (lvl == 2) return new Counts(2, 1, 0);
        if (lvl == 3) return new Counts(2, 2, 1);
        return new Counts(3, 3, 2);
    }

    /* ---------------- Spawning ---------------- */

    private enum Role { FARMER, ARCHER, KNIGHT }

    private static void spawnOne(ServerLevel level, Civilization civ, Role role, BlockPos monument) {
        if (level == null || civ == null || role == null || monument == null) return;

        Mob ent = switch (role) {
            case FARMER -> ModEntities.NPC_FARMER.get().create(level);
            case ARCHER -> ModEntities.NPC_ARCHER.get().create(level);
            case KNIGHT -> ModEntities.NPC_KNIGHT.get().create(level);
        };

        if (ent == null) return;

        BlockPos spawnPos = findSpawnPosNear(level, monument, 6, level.random);
        if (spawnPos == null) return;

        ent.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);

        // Set civ id + role tags (you said you like tags too)
        if (ent instanceof MineCivNpcBase base) {
            base.setCivId(civ.id());
        }
        ent.getPersistentData().putUUID("MineCivCivId", civ.id());
        ent.getPersistentData().putString("MineCivRole", role.name());

        // Spawn
        level.addFreshEntity(ent);

        // Track it
        civ.addNpcId(ent.getUUID());
    }

    /** Find a spawnable position near the monument (no force-load). */
    private static BlockPos findSpawnPosNear(ServerLevel level, BlockPos center, int radius, RandomSource rand) {
        int r = Math.max(2, radius);

        for (int tries = 0; tries < 20; tries++) {
            int dx = rand.nextIntBetweenInclusive(-r, r);
            int dz = rand.nextIntBetweenInclusive(-r, r);

            BlockPos p = center.offset(dx, 0, dz);

            // Find topmost solid ground near p (within a small vertical scan)
            BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, p);

            // Need standing space: block at feet empty and head empty, block below solid
            BlockPos feet = top;
            BlockPos head = top.above();
            BlockPos below = top.below();

            if (!level.getBlockState(below).isSolid()) continue;
            if (!level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()) continue;
            if (!level.getBlockState(head).getCollisionShape(level, head).isEmpty()) continue;

            // Avoid spawning inside the monument itself
            if (feet.closerThan(center, 2.0)) continue;

            return feet;
        }

        // fallback: 1 block above monument
        BlockPos fallback = center.above();
        if (level.getBlockState(fallback).getCollisionShape(level, fallback).isEmpty()) return fallback;

        return null;
    }

    /* ---------------- Counting / cleanup ---------------- */

    private static void cleanupNpcIds(MinecraftServer server, Civilization civ) {
        List<UUID> ids = new ArrayList<>(civ.npcIds());
        for (UUID id : ids) {
            if (id == null) {
                civ.removeNpcId(null);
                continue;
            }

            boolean found = false;
            for (ServerLevel dim : server.getAllLevels()) {
                var e = dim.getEntity(id);
                if (e != null) {
                    found = true;
                    break;
                }
            }

            if (!found) civ.removeNpcId(id);
        }
    }

    private static Counts countExisting(MinecraftServer server, Civilization civ) {
        int farmers = 0, archers = 0, knights = 0;

        for (UUID id : civ.npcIds()) {
            if (id == null) continue;

            for (ServerLevel dim : server.getAllLevels()) {
                var e = dim.getEntity(id);
                if (e == null) continue;

                if (e instanceof MineCivFarmerNpc) farmers++;
                else if (e instanceof MineCivKnightNpc) archers++;
                else if (e instanceof MineCivKnightNpc) knights++;
                break;
            }
        }

        return new Counts(farmers, archers, knights);
    }

    private record Counts(int farmers, int archers, int knights) {}
}
