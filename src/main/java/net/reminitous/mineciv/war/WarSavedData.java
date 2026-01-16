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

    // warId -> WarState
    private final Map<UUID, WarState> wars = new HashMap<>();

    // civId -> activeWarId (ACTIVE only; null/absent if none)
    private final Map<UUID, UUID> activeByCiv = new HashMap<>();

    // civId -> pendingWarId (any non-ended: PROPOSED or PREPARING or ACTIVE)
    // This is how we enforce "only one proposal allowed at a time per civ".
    private final Map<UUID, UUID> pendingByCiv = new HashMap<>();

    public WarSavedData() {}

    public static WarSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld is null");
        return get(overworld);
    }

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

    public Map<UUID, WarState> wars() { return wars; }

    public WarState getWar(UUID warId) { return warId == null ? null : wars.get(warId); }

    public void putWar(WarState war) {
        if (war == null) return;
        wars.put(war.warId(), war);
        setDirty();
    }

    public void removeWar(UUID warId) {
        if (warId == null) return;
        wars.remove(warId);
        activeByCiv.values().removeIf(w -> w != null && w.equals(warId));
        pendingByCiv.values().removeIf(w -> w != null && w.equals(warId));
        setDirty();
    }

    /* ---------------- ACTIVE mapping (ACTIVE only) ---------------- */

    public UUID getActiveWarId(UUID civId) {
        if (civId == null) return null;
        return activeByCiv.get(civId);
    }

    public void setActiveWar(UUID civId, UUID warIdOrNull) {
        if (civId == null) return;
        if (warIdOrNull == null) activeByCiv.remove(civId);
        else activeByCiv.put(civId, warIdOrNull);
        setDirty();
    }

    /* ---------------- PENDING mapping (PROPOSED/PREPARING/ACTIVE) ---------------- */

    public UUID getPendingWarId(UUID civId) {
        if (civId == null) return null;
        return pendingByCiv.get(civId);
    }

    public void setPendingWar(UUID civId, UUID warIdOrNull) {
        if (civId == null) return;
        if (warIdOrNull == null) pendingByCiv.remove(civId);
        else pendingByCiv.put(civId, warIdOrNull);
        setDirty();
    }

    public void clearPendingForWar(UUID warId) {
        if (warId == null) return;
        pendingByCiv.entrySet().removeIf(e -> warId.equals(e.getValue()));
        setDirty();
    }

    /* ---------------- NBT ---------------- */

    public static WarSavedData load(CompoundTag root, HolderLookup.Provider provider) {
        WarSavedData data = new WarSavedData();

        // Wars
        ListTag warsList = root.getList("Wars", 10);
        for (int i = 0; i < warsList.size(); i++) {
            CompoundTag t = warsList.getCompound(i);
            WarState w = WarState.fromNbt(t, provider);
            data.wars.put(w.warId(), w);
        }

        // ActiveByCiv
        ListTag activeList = root.getList("ActiveByCiv", 10);
        for (int i = 0; i < activeList.size(); i++) {
            CompoundTag t = activeList.getCompound(i);
            UUID civ = t.getUUID("Civ");
            UUID war = t.getUUID("War");
            if (civ != null && war != null) data.activeByCiv.put(civ, war);
        }

        // PendingByCiv
        ListTag pendingList = root.getList("PendingByCiv", 10);
        for (int i = 0; i < pendingList.size(); i++) {
            CompoundTag t = pendingList.getCompound(i);
            UUID civ = t.getUUID("Civ");
            UUID war = t.getUUID("War");
            if (civ != null && war != null) data.pendingByCiv.put(civ, war);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {
        // Wars
        ListTag warsList = new ListTag();
        for (WarState w : wars.values()) {
            warsList.add(w.toNbt());
        }
        root.put("Wars", warsList);

        // ActiveByCiv
        ListTag activeList = new ListTag();
        for (Map.Entry<UUID, UUID> e : activeByCiv.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            CompoundTag t = new CompoundTag();
            t.putUUID("Civ", e.getKey());
            t.putUUID("War", e.getValue());
            activeList.add(t);
        }
        root.put("ActiveByCiv", activeList);

        // PendingByCiv
        ListTag pendingList = new ListTag();
        for (Map.Entry<UUID, UUID> e : pendingByCiv.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            CompoundTag t = new CompoundTag();
            t.putUUID("Civ", e.getKey());
            t.putUUID("War", e.getValue());
            pendingList.add(t);
        }
        root.put("PendingByCiv", pendingList);

        return root;
    }
}
