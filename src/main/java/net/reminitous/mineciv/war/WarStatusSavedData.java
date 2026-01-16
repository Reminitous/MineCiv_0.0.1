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

/**
 * Holds post-war cooldowns + production tax state.
 * Stored globally in the Overworld data storage.
 */
public final class WarStatusSavedData extends SavedData {

    public static final String NAME = "mineciv_war_status";

    // CivId -> graceUntilMs (loser cannot receive/send proposals during grace)
    private final Map<UUID, Long> graceUntil = new HashMap<>();

    // Pair cooldown: winner->loser rematch until
    // key = pairKey(winner, loser) -> untilMs
    private final Map<String, Long> rematchUntil = new HashMap<>();

    // Production tax for loser civ:
    // loserCivId -> TaxRecord
    private final Map<UUID, TaxRecord> taxByLoser = new HashMap<>();

    public WarStatusSavedData() {}

    public static WarStatusSavedData get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) throw new IllegalStateException("Overworld is null");
        return get(overworld);
    }

    public static WarStatusSavedData get(ServerLevel level) {
        DimensionDataStorage storage = level.getDataStorage();
        return storage.computeIfAbsent(
                new SavedData.Factory<>(
                        WarStatusSavedData::new,
                        WarStatusSavedData::load,
                        DataFixTypes.LEVEL
                ),
                NAME
        );
    }

    /* ---------------- Grace ---------------- */

    /** Set grace-until time (ms epoch). */
    public void setGrace(UUID civId, long untilMs) {
        if (civId == null) return;
        if (untilMs <= 0L) graceUntil.remove(civId);
        else graceUntil.put(civId, untilMs);
        setDirty();
    }

    /** Returns grace-until epoch ms; 0 if none. */
    public long getGraceUntil(UUID civId) {
        if (civId == null) return 0L;
        Long v = graceUntil.get(civId);
        return v == null ? 0L : v;
    }

    /** True if civ is currently in grace at time nowMs. */
    public boolean isInGrace(UUID civId, long nowMs) {
        long until = getGraceUntil(civId);
        if (until <= 0L) return false;
        if (nowMs <= until) return true;
        // expired -> cleanup
        graceUntil.remove(civId);
        setDirty();
        return false;
    }

    /* ---------------- Rematch cooldown ---------------- */

    private static String pairKey(UUID winner, UUID loser) {
        // Winner/loser order matters.
        return String.valueOf(winner) + "->" + String.valueOf(loser);
    }

    /** Winner cannot war loser again until this time (ms epoch). */
    public void setRematch(UUID winnerCivId, UUID loserCivId, long untilMs) {
        if (winnerCivId == null || loserCivId == null) return;
        String key = pairKey(winnerCivId, loserCivId);
        if (untilMs <= 0L) rematchUntil.remove(key);
        else rematchUntil.put(key, untilMs);
        setDirty();
    }

    /** Returns rematch-until epoch ms; 0 if none. */
    public long getRematchUntil(UUID winnerCivId, UUID loserCivId) {
        if (winnerCivId == null || loserCivId == null) return 0L;
        Long v = rematchUntil.get(pairKey(winnerCivId, loserCivId));
        return v == null ? 0L : v;
    }

    /** True if winner->loser rematch is blocked at time nowMs. */
    public boolean isRematchBlocked(UUID winnerCivId, UUID loserCivId, long nowMs) {
        long until = getRematchUntil(winnerCivId, loserCivId);
        if (until <= 0L) return false;
        if (nowMs <= until) return true;
        // expired -> cleanup
        rematchUntil.remove(pairKey(winnerCivId, loserCivId));
        setDirty();
        return false;
    }

    /* ---------------- Tax storage ---------------- */

    public void setTax(UUID loserCivId, UUID winnerCivId, int bps, long untilMs) {
        if (loserCivId == null) return;

        if (winnerCivId == null || bps <= 0 || untilMs <= 0L) {
            taxByLoser.remove(loserCivId);
            setDirty();
            return;
        }

        TaxRecord tr = new TaxRecord(winnerCivId, bps, untilMs);
        taxByLoser.put(loserCivId, tr);
        setDirty();
    }

    public UUID getTaxWinnerCivId(UUID loserCivId) {
        TaxRecord tr = taxByLoser.get(loserCivId);
        return tr == null ? null : tr.winnerCivId;
    }

    public int getTaxBps(UUID loserCivId) {
        TaxRecord tr = taxByLoser.get(loserCivId);
        return tr == null ? 0 : tr.bps;
    }

    public long getTaxUntilMs(UUID loserCivId) {
        TaxRecord tr = taxByLoser.get(loserCivId);
        return tr == null ? 0L : tr.untilMs;
    }

    public void clearTax(UUID loserCivId) {
        if (loserCivId == null) return;
        if (taxByLoser.remove(loserCivId) != null) {
            setDirty();
        }
    }

    /* ---------------- NBT ---------------- */

    public static WarStatusSavedData load(CompoundTag root, HolderLookup.Provider provider) {
        WarStatusSavedData data = new WarStatusSavedData();

        // Grace
        ListTag graceList = root.getList("Grace", 10);
        for (int i = 0; i < graceList.size(); i++) {
            CompoundTag t = graceList.getCompound(i);
            UUID civ = t.getUUID("Civ");
            long until = t.getLong("Until");
            if (civ != null && until > 0L) data.graceUntil.put(civ, until);
        }

        // Rematch
        ListTag rematchList = root.getList("Rematch", 10);
        for (int i = 0; i < rematchList.size(); i++) {
            CompoundTag t = rematchList.getCompound(i);
            String key = t.getString("Key");
            long until = t.getLong("Until");
            if (key != null && !key.isEmpty() && until > 0L) data.rematchUntil.put(key, until);
        }

        // Tax
        ListTag taxList = root.getList("Tax", 10);
        for (int i = 0; i < taxList.size(); i++) {
            CompoundTag t = taxList.getCompound(i);
            UUID loser = t.getUUID("Loser");
            UUID winner = t.getUUID("Winner");
            int bps = t.getInt("Bps");
            long until = t.getLong("Until");
            if (loser != null && winner != null && bps > 0 && until > 0L) {
                data.taxByLoser.put(loser, new TaxRecord(winner, bps, until));
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {
        // Grace
        ListTag graceList = new ListTag();
        for (Map.Entry<UUID, Long> e : graceUntil.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            if (e.getValue() <= 0L) continue;
            CompoundTag t = new CompoundTag();
            t.putUUID("Civ", e.getKey());
            t.putLong("Until", e.getValue());
            graceList.add(t);
        }
        root.put("Grace", graceList);

        // Rematch
        ListTag rematchList = new ListTag();
        for (Map.Entry<String, Long> e : rematchUntil.entrySet()) {
            if (e.getKey() == null || e.getKey().isEmpty() || e.getValue() == null) continue;
            if (e.getValue() <= 0L) continue;
            CompoundTag t = new CompoundTag();
            t.putString("Key", e.getKey());
            t.putLong("Until", e.getValue());
            rematchList.add(t);
        }
        root.put("Rematch", rematchList);

        // Tax
        ListTag taxList = new ListTag();
        for (Map.Entry<UUID, TaxRecord> e : taxByLoser.entrySet()) {
            UUID loser = e.getKey();
            TaxRecord tr = e.getValue();
            if (loser == null || tr == null) continue;
            if (tr.winnerCivId == null || tr.bps <= 0 || tr.untilMs <= 0L) continue;
            CompoundTag t = new CompoundTag();
            t.putUUID("Loser", loser);
            t.putUUID("Winner", tr.winnerCivId);
            t.putInt("Bps", tr.bps);
            t.putLong("Until", tr.untilMs);
            taxList.add(t);
        }
        root.put("Tax", taxList);

        return root;
    }

    /* ---------------- Internal record ---------------- */

    private static final class TaxRecord {
        private final UUID winnerCivId;
        private final int bps;
        private final long untilMs;

        private TaxRecord(UUID winnerCivId, int bps, long untilMs) {
            this.winnerCivId = winnerCivId;
            this.bps = bps;
            this.untilMs = untilMs;
        }
    }
}
