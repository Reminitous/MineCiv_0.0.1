package net.reminitous.mineciv.territory;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.UUID;

public final class TerritoryManager {

    private static final int MAX_CHUNKS = 100;     // 10x10
    private static final int MAX_SPAN = 10;        // max width/height in chunk coords

    private TerritoryManager() {}

    public static UUID getOwnerCivId(ServerLevel level, ChunkPos cp) {
        CivSavedData data = CivSavedData.get(level.getServer());
        return data.getChunkOwner(cp.toLong());
    }

    public static UUID getOwnerCivId(ServerLevel level, net.minecraft.core.BlockPos pos) {
        return getOwnerCivId(level, new ChunkPos(pos));
    }

    /**
     * Claims a chunk for civId, enforcing:
     * - chunk must be unclaimed
     * - total claims <= 100
     * - expansion must be edge-adjacent (except first claim)
     * - claims must fit inside a 10x10 bounding box
     * - requires claim credits for expansion
     */
    public static boolean claimChunk(ServerLevel level, UUID civId, ChunkPos cp) {
        CivSavedData data = CivSavedData.get(level.getServer());

        if (data.getChunkOwner(cp.toLong()) != null) return false;

        Civilization civ = data.getCiv(civId);
        if (civ == null) return false;

        int current = civ.claimedChunks().size();
        if (current >= MAX_CHUNKS) return false;

        // Adjacency rule (skip if first claim)
        if (current > 0 && !isAdjacentToExisting(civ, cp)) return false;

        // Bounding 10x10 rule
        if (!fitsBoundingBox(civ, cp)) return false;

        // ---- CLAIM CREDIT GATE ----
        // First chunk (monument) is free
        if (current > 0) {
            if (!civ.spendClaimCredit()) return false;
        }

        // Apply claim
        data.setChunkOwner(cp.toLong(), civId);
        civ.addClaimedChunk(cp.toLong());
        data.putCiv(civ);

        return true;
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
}
