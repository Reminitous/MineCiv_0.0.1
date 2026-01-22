package net.reminitous.mineciv.territory;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.UUID;

public final class TerritoryManager {

    public static final int MAX_CHUNKS = 100;     // 10x10
    public static final int MAX_SPAN  = 10;       // max width/height in chunk coords

    // NEW: at least 3 chunk gap from any other civ territory boundary
    public static final int MIN_TERRITORY_GAP = 3;

    private TerritoryManager() {}

    public enum ClaimResult {
        SUCCESS,
        ALREADY_CLAIMED,
        CIV_NOT_FOUND,
        MAX_CHUNKS_REACHED,
        NOT_ADJACENT,
        TOO_WIDE,
        TOO_CLOSE_TO_OTHER_TERRITORY
    }

    public static UUID getOwnerCivId(ServerLevel level, ChunkPos cp) {
        CivSavedData data = CivSavedData.get(level.getServer());
        return data.getChunkOwner(cp.toLong());
    }

    public static UUID getOwnerCivId(ServerLevel level, BlockPos pos) {
        return getOwnerCivId(level, new ChunkPos(pos));
    }

    public static ClaimResult claimChunkDetailed(ServerLevel level, UUID civId, ChunkPos cp) {
        CivSavedData data = CivSavedData.get(level.getServer());

        if (data.getChunkOwner(cp.toLong()) != null) return ClaimResult.ALREADY_CLAIMED;

        Civilization civ = data.getCiv(civId);
        if (civ == null) return ClaimResult.CIV_NOT_FOUND;

        int current = civ.claimedChunks().size();
        if (current >= MAX_CHUNKS) return ClaimResult.MAX_CHUNKS_REACHED;

        // NEW: gap rule vs other civs (skip if you want first claim to ignore this — but you said ALL territories)
        if (isTooCloseToOtherTerritory(data, civId, cp, MIN_TERRITORY_GAP)) {
            return ClaimResult.TOO_CLOSE_TO_OTHER_TERRITORY;
        }

        // Adjacency rule (skip if first claim)
        if (current > 0 && !isAdjacentToExisting(civ, cp)) return ClaimResult.NOT_ADJACENT;

        // Bounding box rule
        if (!fitsBoundingBox(civ, cp)) return ClaimResult.TOO_WIDE;

        // Apply claim
        data.setChunkOwner(cp.toLong(), civId);
        civ.addClaimedChunk(cp.toLong());
        data.putCiv(civ);

        return ClaimResult.SUCCESS;
    }

    public static boolean claimChunk(ServerLevel level, UUID civId, ChunkPos cp) {
        return claimChunkDetailed(level, civId, cp) == ClaimResult.SUCCESS;
    }

    public static void unclaimChunk(ServerLevel level, UUID civId, ChunkPos cp) {
        CivSavedData data = CivSavedData.get(level.getServer());
        UUID owner = data.getChunkOwner(cp.toLong());
        if (owner == null || !owner.equals(civId)) return;

        Civilization civ = data.getCiv(civId);
        if (civ != null) {
            civ.removeClaimedChunk(cp.toLong());
            data.putCiv(civ);
        }
        data.setChunkOwner(cp.toLong(), null);
    }

    private static boolean isAdjacentToExisting(Civilization civ, ChunkPos cp) {
        int x = cp.x;
        int z = cp.z;

        long n = new ChunkPos(x, z - 1).toLong();
        long s = new ChunkPos(x, z + 1).toLong();
        long w = new ChunkPos(x - 1, z).toLong();
        long e = new ChunkPos(x + 1, z).toLong();

        return civ.claimedChunks().contains(n)
                || civ.claimedChunks().contains(s)
                || civ.claimedChunks().contains(w)
                || civ.claimedChunks().contains(e);
    }

    private static boolean fitsBoundingBox(Civilization civ, ChunkPos candidate) {
        if (civ.claimedChunks().isEmpty()) return true;

        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (long l : civ.claimedChunks()) {
            ChunkPos p = new ChunkPos(l);
            if (p.x < minX) minX = p.x;
            if (p.x > maxX) maxX = p.x;
            if (p.z < minZ) minZ = p.z;
            if (p.z > maxZ) maxZ = p.z;
        }

        minX = Math.min(minX, candidate.x);
        maxX = Math.max(maxX, candidate.x);
        minZ = Math.min(minZ, candidate.z);
        maxZ = Math.max(maxZ, candidate.z);

        int spanX = maxX - minX + 1;
        int spanZ = maxZ - minZ + 1;

        return spanX <= MAX_SPAN && spanZ <= MAX_SPAN;
    }

    /**
     * Returns true if candidate is within `gap` chunks of ANY chunk owned by a DIFFERENT civ.
     * Uses Chebyshev distance (square radius): |dx|<=gap AND |dz|<=gap.
     */
    private static boolean isTooCloseToOtherTerritory(CivSavedData data, UUID claimingCivId, ChunkPos candidate, int gap) {
        int cx = candidate.x;
        int cz = candidate.z;

        for (var entry : data.chunkOwner().entrySet()) {
            UUID owner = entry.getValue();
            if (owner == null) continue;
            if (owner.equals(claimingCivId)) continue;

            ChunkPos other = new ChunkPos(entry.getKey());
            int dx = Math.abs(other.x - cx);
            int dz = Math.abs(other.z - cz);

            if (dx <= gap && dz <= gap) {
                return true;
            }
        }

        return false;
    }
}
