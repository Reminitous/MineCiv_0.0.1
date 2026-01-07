package net.reminitous.mineciv.war;

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

public final class WarSavedData extends SavedData {

    public static final String NAME = "mineciv_wars";

    private final Map<UUID, WarState> wars = new HashMap<>();
    private final Map<UUID, UUID> civToActiveWar = new HashMap<>(); // civId -> warId

    /* ---------------- Accessors ---------------- */

    public Map<UUID, WarState> wars() { return wars; }
    public Map<UUID, UUID> civToActiveWar() { return civToActiveWar; }

    public WarState getWar(UUID warId) { return wars.get(warId); }

    public UUID getActiveWarId(UUID civId) { return civToActiveWar.get(civId); }

    public WarState getActiveWar(UUID civId) {
        UUID warId = civToActiveWar.get(civId);
        return warId == null ? null : wars.get(warId);
    }

    public void putWar(WarState war) {
        if (war == null) return;
        wars.put(war.warId(), war);
        setDirty();
    }

    public void removeWar(UUID warId) {
        if (warId == null) return;
        wars.remove(warId);
        civToActiveWar.entrySet().removeIf(e -> warId.equals(e.getValue()));
        setDirty();
    }

    public void setActiveWar(UUID civId, UUID warId) {
        if (civId == null) return;
        if (warId == null) civToActiveWar.remove(civId);
        else civToActiveWar.put(civId, warId);
        setDirty();
    }

    /* ---------------- Storage ---------------- */

    public static WarSavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(
                        WarSavedData::new,
                        WarSavedData::load,
                        DataFixTypes.LEVEL
                ),
                NAME
        );
    }

    public static WarSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld is null");
        return get(overworld);
    }

    public static WarSavedData load(CompoundTag root, HolderLookup.Provider provider) {
        WarSavedData data = new WarSavedData();

        // Wars list
        ListTag warsList = root.getList("Wars", 10);
        for (int i = 0; i < warsList.size(); i++) {
            CompoundTag t = warsList.getCompound(i);
            WarState war = WarState.fromNbt(t);
            data.wars.put(war.warId(), war);
        }

        // Civ -> ActiveWar mapping
        ListTag mapList = root.getList("CivToActiveWar", 10);
        for (int i = 0; i < mapList.size(); i++) {
            CompoundTag t = mapList.getCompound(i);
            UUID civId = t.getUUID("CivId");
            UUID warId = t.getUUID("WarId");
            data.civToActiveWar.put(civId, warId);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {
        // Wars
        ListTag warsList = new ListTag();
        for (WarState war : wars.values()) {
            warsList.add(war.toNbt());
        }
        root.put("Wars", warsList);

        // Civ -> ActiveWar
        ListTag mapList = new ListTag();
        for (Map.Entry<UUID, UUID> e : civToActiveWar.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            CompoundTag t = new CompoundTag();
            t.putUUID("CivId", e.getKey());
            t.putUUID("WarId", e.getValue());
            mapList.add(t);
        }
        root.put("CivToActiveWar", mapList);

        return root;
    }
}
