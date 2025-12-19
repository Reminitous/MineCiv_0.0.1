package net.reminitous.mineciv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChunkClaimManager extends SavedData {
    private static final String DATA_NAME = "mineciv_chunk_claims";

    // Map of chunk coordinates to claim data: "chunkX,chunkZ" -> ClaimData
    private final Map<String, ClaimData> claims = new HashMap<>();

    public static class ClaimData {
        public UUID ownerUUID;
        public BlockPos monumentPos;

        public ClaimData(UUID ownerUUID, BlockPos monumentPos) {
            this.ownerUUID = ownerUUID;
            this.monumentPos = monumentPos;
        }
    }

    public ChunkClaimManager() {
        super();
    }

    public static ChunkClaimManager load(CompoundTag tag, HolderLookup.Provider provider) {
        ChunkClaimManager manager = new ChunkClaimManager();
        ListTag claimsList = tag.getList("claims", Tag.TAG_COMPOUND);

        for (int i = 0; i < claimsList.size(); i++) {
            CompoundTag claimTag = claimsList.getCompound(i);
            String key = claimTag.getString("key");
            UUID ownerUUID = claimTag.getUUID("owner");
            BlockPos monumentPos = new BlockPos(
                    claimTag.getInt("monumentX"),
                    claimTag.getInt("monumentY"),
                    claimTag.getInt("monumentZ")
            );
            manager.claims.put(key, new ClaimData(ownerUUID, monumentPos));
        }

        return manager;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag claimsList = new ListTag();

        for (Map.Entry<String, ClaimData> entry : claims.entrySet()) {
            CompoundTag claimTag = new CompoundTag();
            claimTag.putString("key", entry.getKey());
            claimTag.putUUID("owner", entry.getValue().ownerUUID);
            claimTag.putInt("monumentX", entry.getValue().monumentPos.getX());
            claimTag.putInt("monumentY", entry.getValue().monumentPos.getY());
            claimTag.putInt("monumentZ", entry.getValue().monumentPos.getZ());
            claimsList.add(claimTag);
        }

        tag.put("claims", claimsList);
        return tag;
    }

    private static ChunkClaimManager get(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalStateException("Cannot access ChunkClaimManager on client side!");
        }

        DimensionDataStorage storage = serverLevel.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(
                        ChunkClaimManager::new,
                        ChunkClaimManager::load,
                        null
                ),
                DATA_NAME
        );
    }

    public static boolean claimChunk(Level level, int chunkX, int chunkZ, UUID ownerUUID, BlockPos monumentPos) {
        ChunkClaimManager manager = get(level);
        String key = chunkX + "," + chunkZ;

        if (manager.claims.containsKey(key)) {
            return false; // Already claimed
        }

        manager.claims.put(key, new ClaimData(ownerUUID, monumentPos));
        manager.setDirty();
        return true;
    }

    public static void unclaimChunk(Level level, int chunkX, int chunkZ, BlockPos monumentPos) {
        ChunkClaimManager manager = get(level);
        String key = chunkX + "," + chunkZ;

        ClaimData claim = manager.claims.get(key);
        if (claim != null && claim.monumentPos.equals(monumentPos)) {
            manager.claims.remove(key);
            manager.setDirty();
        }
    }

    public static ClaimData getClaim(Level level, int chunkX, int chunkZ) {
        ChunkClaimManager manager = get(level);
        return manager.claims.get(chunkX + "," + chunkZ);
    }

    public static boolean isChunkClaimed(Level level, int chunkX, int chunkZ) {
        return getClaim(level, chunkX, chunkZ) != null;
    }
}