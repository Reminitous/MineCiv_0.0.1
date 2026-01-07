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

public final class WarHealthSavedData extends SavedData {

    public static final String NAME = "mineciv_war_health";

    /** warId -> record */
    private final Map<UUID, WarHealthRecord> healthByWar = new HashMap<>();

    public Map<UUID, WarHealthRecord> healthByWar() { return healthByWar; }

    public WarHealthRecord get(UUID warId) { return healthByWar.get(warId); }

    public void put(WarHealthRecord rec) {
        if (rec == null) return;
        healthByWar.put(rec.warId, rec);
        setDirty();
    }

    public void remove(UUID warId) {
        if (warId == null) return;
        healthByWar.remove(warId);
        setDirty();
    }

    /* ---------------- Storage ---------------- */

    public static WarHealthSavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(
                        WarHealthSavedData::new,
                        WarHealthSavedData::load,
                        DataFixTypes.LEVEL
                ),
                NAME
        );
    }

    public static WarHealthSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld is null");
        return get(overworld);
    }

    public static WarHealthSavedData load(CompoundTag root, HolderLookup.Provider provider) {
        WarHealthSavedData data = new WarHealthSavedData();

        ListTag list = root.getList("WarHealth", 10);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            WarHealthRecord rec = WarHealthRecord.fromNbt(t);
            data.healthByWar.put(rec.warId, rec);
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (WarHealthRecord rec : healthByWar.values()) {
            list.add(rec.toNbt());
        }
        root.put("WarHealth", list);
        return root;
    }

    /* ---------------- Record ---------------- */

    public static final class WarHealthRecord {
        public final UUID warId;

        public UUID attackerCivId;
        public UUID defenderCivId;

        public long attackerStartValue;
        public long defenderStartValue;

        public long attackerHealth;
        public long defenderHealth;

        public long createdAtMs;

        public WarHealthRecord(UUID warId) {
            this.warId = warId;
        }

        public CompoundTag toNbt() {
            CompoundTag t = new CompoundTag();
            t.putUUID("WarId", warId);

            if (attackerCivId != null) t.putUUID("AttackerCivId", attackerCivId);
            if (defenderCivId != null) t.putUUID("DefenderCivId", defenderCivId);

            t.putLong("AttackerStartValue", attackerStartValue);
            t.putLong("DefenderStartValue", defenderStartValue);

            t.putLong("AttackerHealth", attackerHealth);
            t.putLong("DefenderHealth", defenderHealth);

            t.putLong("CreatedAtMs", createdAtMs);
            return t;
        }

        public static WarHealthRecord fromNbt(CompoundTag t) {
            UUID warId = t.getUUID("WarId");
            WarHealthRecord rec = new WarHealthRecord(warId);

            if (t.hasUUID("AttackerCivId")) rec.attackerCivId = t.getUUID("AttackerCivId");
            if (t.hasUUID("DefenderCivId")) rec.defenderCivId = t.getUUID("DefenderCivId");

            rec.attackerStartValue = t.getLong("AttackerStartValue");
            rec.defenderStartValue = t.getLong("DefenderStartValue");

            rec.attackerHealth = t.getLong("AttackerHealth");
            rec.defenderHealth = t.getLong("DefenderHealth");

            rec.createdAtMs = t.getLong("CreatedAtMs");

            return rec;
        }
    }
}
