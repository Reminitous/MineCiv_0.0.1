package net.reminitous.mineciv.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.reminitous.mineciv.civ.Civilization;

public final class CivTerritoryUtil {

    private CivTerritoryUtil() {}

    /** Packs chunk coords into a long key that matches your Set<Long> claimedChunks. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }

    public static long chunkKey(ChunkPos pos) {
        return chunkKey(pos.x, pos.z);
    }

    /** True if a block position is inside this civ's claimed chunks. */
    public static boolean isInTerritory(Civilization civ, BlockPos pos) {
        if (civ == null || pos == null) return false;
        ChunkPos cp = new ChunkPos(pos);
        return civ.claimedChunks().contains(chunkKey(cp));
    }

    public static boolean isInOrNearTerritory(Civilization civ, BlockPos pos, int extraChunks) {
        if (civ == null || pos == null) return false;
        if (extraChunks <= 0) return isInTerritory(civ, pos);

        ChunkPos cp = new ChunkPos(pos);
        int cx = cp.x;
        int cz = cp.z;

        // If current chunk is claimed, we’re good
        if (civ.claimedChunks().contains(chunkKey(cx, cz))) return true;

        // Otherwise, check neighborhood up to extraChunks away
        for (int dx = -extraChunks; dx <= extraChunks; dx++) {
            for (int dz = -extraChunks; dz <= extraChunks; dz++) {
                if (civ.claimedChunks().contains(chunkKey(cx + dx, cz + dz))) return true;
            }
        }

        return false;
    }

    public static boolean isInOrNearTerritory(Civilization civ, BlockPos pos, int extraChunks, boolean requireClaimedIfEmpty) {
        if (civ == null || pos == null) return false;

        // If civ has no claimed chunks yet, optionally fall back to “near monument” behavior elsewhere.
        if (requireClaimedIfEmpty && (civ.claimedChunks() == null || civ.claimedChunks().isEmpty())) return false;

        return isInOrNearTerritory(civ, pos, extraChunks);
    }

}
