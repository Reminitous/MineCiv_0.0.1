package net.reminitous.mineciv.civ;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CivSavedData extends SavedData {

    public static final String NAME = "mineciv";

    /* ---------------- DATA ---------------- */

    private final Map<UUID, Civilization> civs = new HashMap<>();
    private final Map<Long, UUID> chunkOwner = new HashMap<>();
    private final Map<UUID, UUID> playerToCiv = new HashMap<>();

    /* ---------------- ACCESS ---------------- */

    /** Dimension-safe access (use this everywhere) */
    public static CivSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld is null");
        return get(overworld);
    }

    /** Internal level access */
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

    /* ---------------- LOOKUPS ---------------- */

    public Civilization getCiv(UUID civId) {
        return civs.get(civId);
    }

    public Map<UUID, Civilization> civs() {
        return civs;
    }

    public UUID getChunkOwner(long chunkLong) {
        return chunkOwner.get(chunkLong);
    }

    public UUID getPlayersCiv(UUID playerId) {
        return playerToCiv.get(playerId);
    }

    /* ---------------- MUTATION ---------------- */

    public void putCiv(Civilization civ) {
        civs.put(civ.id(), civ);
        setDirty();
    }

    public void removeCiv(UUID civId) {
        civs.remove(civId);
        chunkOwner.entrySet().removeIf(e -> e.getValue().equals(civId));
        playerToCiv.entrySet().removeIf(e -> e.getValue().equals(civId));
        setDirty();
    }

    public void setChunkOwner(long chunkLong, UUID civId) {
        if (civId == null) chunkOwner.remove(chunkLong);
        else chunkOwner.put(chunkLong, civId);
        setDirty();
    }

    public void setPlayersCiv(UUID playerId, UUID civId) {
        if (civId == null) playerToCiv.remove(playerId);
        else playerToCiv.put(playerId, civId);
        setDirty();
    }

    /* ---------------- LOAD ---------------- */

    public static CivSavedData load(CompoundTag root, HolderLookup.Provider provider) {
        CivSavedData data = new CivSavedData();

        // Civs
        ListTag civList = root.getList("Civs", 10);
        for (int i = 0; i < civList.size(); i++) {
            Civilization civ = Civilization.fromNbt(civList.getCompound(i));
            data.civs.put(civ.id(), civ);
        }

        // Chunk ownership
        ListTag ownerList = root.getList("ChunkOwner", 10);
        for (int i = 0; i < ownerList.size(); i++) {
            CompoundTag t = ownerList.getCompound(i);
            data.chunkOwner.put(t.getLong("Chunk"), t.getUUID("CivId"));
        }

        // Player → Civ mapping
        ListTag playerList = root.getList("PlayerToCiv", 10);
        for (int i = 0; i < playerList.size(); i++) {
            CompoundTag t = playerList.getCompound(i);
            data.playerToCiv.put(t.getUUID("Player"), t.getUUID("CivId"));
        }

        ListTag warList = root.getList("Wars", 10);
        for (int i = 0; i < warList.size(); i++) {
            CompoundTag t = warList.getCompound(i);
            var wr = net.reminitous.mineciv.war.WarRecord.fromNbt(t);
            data.wars.put(wr.warId, wr);
        }

        return data;
    }

    /* ---------------- SAVE ---------------- */

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {

        // Civs
        ListTag civList = new ListTag();
        for (Civilization civ : civs.values()) {
            civList.add(civ.toNbt());
        }
        root.put("Civs", civList);

        // Chunk ownership
        ListTag ownerList = new ListTag();
        for (Map.Entry<Long, UUID> e : chunkOwner.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putLong("Chunk", e.getKey());
            t.putUUID("CivId", e.getValue());
            ownerList.add(t);
        }
        root.put("ChunkOwner", ownerList);

        // Player → Civ mapping
        ListTag playerList = new ListTag();
        for (Map.Entry<UUID, UUID> e : playerToCiv.entrySet()) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Player", e.getKey());
            t.putUUID("CivId", e.getValue());
            playerList.add(t);
        }
        root.put("PlayerToCiv", playerList);

        ListTag warList = new ListTag();
        for (var wr : wars.values()) {
            warList.add(wr.toNbt());
        }
        root.put("Wars", warList);

        return root;
    }

    private final Map<UUID, net.reminitous.mineciv.war.WarRecord> wars = new HashMap<>();
    public Map<UUID, net.reminitous.mineciv.war.WarRecord> wars() { return wars; }

}
