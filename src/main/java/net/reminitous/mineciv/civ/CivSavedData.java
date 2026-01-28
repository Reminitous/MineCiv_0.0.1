package net.reminitous.mineciv.civ;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import net.minecraft.server.MinecraftServer;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CivSavedData extends SavedData {

    public static final String NAME = "mineciv";

    private final Map<UUID, Civilization> civs = new HashMap<>();
    private final Map<Long, UUID> chunkOwner = new HashMap<>();
    private final Map<UUID, UUID> playerToCiv = new HashMap<>();

    // NEW: invited player -> civId
    private final Map<UUID, UUID> pendingInvites = new HashMap<>();

    public static CivSavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(
                        CivSavedData::new,
                        CivSavedData::load,
                        DataFixTypes.LEVEL
                ),
                NAME
        );
    }

    public static CivSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld is null");
        return get(overworld);
    }

    public Map<UUID, Civilization> civs() { return civs; }
    public Map<Long, UUID> chunkOwner() { return chunkOwner; }

    public Civilization getCiv(UUID civId) { return civs.get(civId); }

    public void putCiv(Civilization civ) {
        civs.put(civ.id(), civ);
        setDirty();
    }

    public void removeCiv(UUID civId) {
        civs.remove(civId);
        chunkOwner.entrySet().removeIf(e -> e.getValue().equals(civId));
        pendingInvites.entrySet().removeIf(e -> e.getValue().equals(civId));
        setDirty();
    }

    public UUID getChunkOwner(long chunkLong) { return chunkOwner.get(chunkLong); }

    public void setChunkOwner(long chunkLong, UUID civId) {
        if (civId == null) chunkOwner.remove(chunkLong);
        else chunkOwner.put(chunkLong, civId);
        setDirty();
    }

    public UUID getPlayersCiv(UUID playerId) { return playerToCiv.get(playerId); }

    public void setPlayersCiv(UUID playerId, UUID civId) {
        if (civId == null) playerToCiv.remove(playerId);
        else playerToCiv.put(playerId, civId);
        setDirty();
    }

    // ---- Invites ----
    public UUID getPendingInvite(UUID playerId) { return pendingInvites.get(playerId); }

    public void setPendingInvite(UUID playerId, UUID civId) {
        if (civId == null) pendingInvites.remove(playerId);
        else pendingInvites.put(playerId, civId);
        setDirty();
    }

    public static CivSavedData load(CompoundTag root, HolderLookup.Provider provider) {
        CivSavedData data = new CivSavedData();

        // Civs
        ListTag civList = root.getList("Civs", 10);
        for (int i = 0; i < civList.size(); i++) {
            CompoundTag civTag = civList.getCompound(i);
            Civilization civ = Civilization.fromNbt(civTag);
            data.civs.put(civ.id(), civ);
        }

        // Chunk owners
        ListTag ownerList = root.getList("ChunkOwner", 10);
        for (int i = 0; i < ownerList.size(); i++) {
            CompoundTag t = ownerList.getCompound(i);
            long chunkLong = t.getLong("Chunk");
            UUID civId = t.getUUID("CivId");
            data.chunkOwner.put(chunkLong, civId);
        }

        // Player -> civ mapping
        ListTag mapList = root.getList("PlayerToCiv", 10);
        for (int i = 0; i < mapList.size(); i++) {
            CompoundTag t = mapList.getCompound(i);
            UUID playerId = t.getUUID("Player");
            UUID civId = t.getUUID("CivId");
            data.playerToCiv.put(playerId, civId);
        }

        // Pending invites
        ListTag invList = root.getList("PendingInvites", 10);
        for (int i = 0; i < invList.size(); i++) {
            CompoundTag t = invList.getCompound(i);
            UUID playerId = t.getUUID("Player");
            UUID civId = t.getUUID("CivId");
            data.pendingInvites.put(playerId, civId);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {

        // Civs
        ListTag civList = new ListTag();
        for (Civilization civ : civs.values()) {
            civList.add(civ.toNbt());
        }
        root.put("Civs", civList);

        // Chunk owners
        ListTag ownerList = new ListTag();
        for (Map.Entry<Long, UUID> e : chunkOwner.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Chunk", e.getKey());
            t.putUUID("CivId", e.getValue());
            ownerList.add(t);
        }
        root.put("ChunkOwner", ownerList);

        // Player -> civ mapping
        ListTag mapList = new ListTag();
        for (Map.Entry<UUID, UUID> e : playerToCiv.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Player", e.getKey());
            t.putUUID("CivId", e.getValue());
            mapList.add(t);
        }
        root.put("PlayerToCiv", mapList);

        // Pending invites
        ListTag invList = new ListTag();
        for (Map.Entry<UUID, UUID> e : pendingInvites.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Player", e.getKey());
            t.putUUID("CivId", e.getValue());
            invList.add(t);
        }
        root.put("PendingInvites", invList);

        return root;
    }

    public Map<UUID, UUID> pendingInvites() { return pendingInvites; }

    // --- Aggression tracking (transient, not saved) ---
    private final java.util.Map<java.util.UUID, java.util.Map<java.util.UUID, Long>> recentAggressors = new java.util.HashMap<>();

    /** Mark a player as an aggressor against a civ until expireGameTime. */
    public void markAggressor(java.util.UUID civId, java.util.UUID playerId, long expireGameTime) {
        if (civId == null || playerId == null) return;
        recentAggressors
                .computeIfAbsent(civId, k -> new java.util.HashMap<>())
                .put(playerId, expireGameTime);
    }

    /** True if the player is currently considered hostile to this civ. */
    public boolean isAggressor(java.util.UUID civId, java.util.UUID playerId, long nowGameTime) {
        if (civId == null || playerId == null) return false;
        var map = recentAggressors.get(civId);
        if (map == null) return false;

        Long exp = map.get(playerId);
        if (exp == null) return false;

        if (nowGameTime > exp) {
            map.remove(playerId);
            return false;
        }
        return true;
    }


}
