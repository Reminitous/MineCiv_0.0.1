package net.reminitous.mineciv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.nbt.NbtUtils;

import java.util.*;

public class ChunkClaimManager extends SavedData {
    private static final String DATA_NAME = "mineciv_chunk_claims";

    // Map of chunk coordinates to claim data: "chunkX,chunkZ" -> ClaimData
    private final Map<String, ClaimData> claims = new HashMap<>();

    // Map of player UUID to the chunk owner they have access to
    private final Map<UUID, UUID> playerAccessMap = new HashMap<>();

    public static class ClaimData {
        public UUID ownerUUID;
        public BlockPos monumentPos;
        public Set<UUID> allowedPlayers;

        public ClaimData(UUID ownerUUID, BlockPos monumentPos) {
            this.ownerUUID = ownerUUID;
            this.monumentPos = monumentPos;
            this.allowedPlayers = new HashSet<>();
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

            ClaimData claimData = new ClaimData(ownerUUID, monumentPos);

            // Load allowed players
            ListTag allowedPlayersList = claimTag.getList("allowedPlayers", Tag.TAG_COMPOUND);
            for (int j = 0; j < allowedPlayersList.size(); j++) {
                UUID playerUUID = NbtUtils.loadUUID(allowedPlayersList.get(j));
                claimData.allowedPlayers.add(playerUUID);
            }

            manager.claims.put(key, claimData);
        }

        // Load player access map
        ListTag accessMapList = tag.getList("playerAccessMap", Tag.TAG_COMPOUND);
        for (int i = 0; i < accessMapList.size(); i++) {
            CompoundTag accessTag = accessMapList.getCompound(i);
            UUID playerUUID = accessTag.getUUID("player");
            UUID ownerUUID = accessTag.getUUID("owner");
            manager.playerAccessMap.put(playerUUID, ownerUUID);
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

            // Save allowed players
            ListTag allowedPlayersList = new ListTag();
            for (UUID playerUUID : entry.getValue().allowedPlayers) {
                allowedPlayersList.add(NbtUtils.createUUID(playerUUID));

            }
            claimTag.put("allowedPlayers", allowedPlayersList);

            claimsList.add(claimTag);
        }

        tag.put("claims", claimsList);

        // Save player access map
        ListTag accessMapList = new ListTag();
        for (Map.Entry<UUID, UUID> entry : playerAccessMap.entrySet()) {
            CompoundTag accessTag = new CompoundTag();
            accessTag.putUUID("player", entry.getKey());
            accessTag.putUUID("owner", entry.getValue());
            accessMapList.add(accessTag);
        }
        tag.put("playerAccessMap", accessMapList);

        return tag;
    }

    private static ChunkClaimManager get(LevelAccessor level) {
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

    public static boolean claimChunk(LevelAccessor level, int chunkX, int chunkZ, UUID ownerUUID, BlockPos monumentPos) {
        ChunkClaimManager manager = get(level);
        String key = chunkX + "," + chunkZ;

        if (manager.claims.containsKey(key)) {
            return false; // Already claimed
        }

        manager.claims.put(key, new ClaimData(ownerUUID, monumentPos));
        manager.setDirty();
        return true;
    }

    public static void unclaimChunk(LevelAccessor level, int chunkX, int chunkZ, BlockPos monumentPos) {
        ChunkClaimManager manager = get(level);
        String key = chunkX + "," + chunkZ;

        ClaimData claim = manager.claims.get(key);
        if (claim != null && claim.monumentPos.equals(monumentPos)) {
            // Remove all players' access to this chunk
            for (UUID playerUUID : claim.allowedPlayers) {
                manager.playerAccessMap.remove(playerUUID);
            }

            manager.claims.remove(key);
            manager.setDirty();
        }
    }

    public static ClaimData getClaim(LevelAccessor level, int chunkX, int chunkZ) {
        ChunkClaimManager manager = get(level);
        return manager.claims.get(chunkX + "," + chunkZ);
    }

    public static boolean isChunkClaimed(LevelAccessor level, int chunkX, int chunkZ) {
        return getClaim(level, chunkX, chunkZ) != null;
    }

    public static boolean canPlayerEdit(LevelAccessor level, int chunkX, int chunkZ, UUID playerUUID) {
        ClaimData claim = getClaim(level, chunkX, chunkZ);
        if (claim == null) {
            return true; // Unclaimed chunks can be edited by anyone
        }

        // Owner can always edit
        if (claim.ownerUUID.equals(playerUUID)) {
            return true;
        }

        // Check if player is allowed
        return claim.allowedPlayers.contains(playerUUID);
    }

    public static boolean addPlayerAccess(LevelAccessor level, int chunkX, int chunkZ, UUID playerUUID) {
        ChunkClaimManager manager = get(level);
        ClaimData claim = getClaim(level, chunkX, chunkZ);

        if (claim == null) {
            return false;
        }

        // Check if player already has access to another owner's chunks
        UUID currentOwner = manager.playerAccessMap.get(playerUUID);
        if (currentOwner != null && !currentOwner.equals(claim.ownerUUID)) {
            return false; // Player already has access to another owner's chunks
        }

        // Don't allow owner to add themselves
        if (claim.ownerUUID.equals(playerUUID)) {
            return false;
        }

        claim.allowedPlayers.add(playerUUID);
        manager.playerAccessMap.put(playerUUID, claim.ownerUUID);
        manager.setDirty();
        return true;
    }

    public static boolean removePlayerAccess(LevelAccessor level, int chunkX, int chunkZ, UUID playerUUID) {
        ChunkClaimManager manager = get(level);
        ClaimData claim = getClaim(level, chunkX, chunkZ);

        if (claim == null) {
            return false;
        }

        boolean removed = claim.allowedPlayers.remove(playerUUID);
        if (removed) {
            manager.playerAccessMap.remove(playerUUID);
            manager.setDirty();
        }
        return removed;
    }

    public static Set<UUID> getAllowedPlayers(LevelAccessor level, int chunkX, int chunkZ) {
        ClaimData claim = getClaim(level, chunkX, chunkZ);
        if (claim == null) {
            return new HashSet<>();
        }
        return new HashSet<>(claim.allowedPlayers);
    }
}