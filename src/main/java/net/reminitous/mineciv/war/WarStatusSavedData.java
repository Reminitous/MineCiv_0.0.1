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

public final class WarStatusSavedData extends SavedData {

    public static final String NAME = "mineciv_war_status";

    // --- Tax: loser civ -> winner civ / bps / until ---
    private final Map<UUID, UUID> taxWinnerByLoser = new HashMap<>();
    private final Map<UUID, Integer> taxBpsByLoser = new HashMap<>();
    private final Map<UUID, Long> taxUntilByLoser = new HashMap<>();

    // --- Grace: civ -> until epoch ms (cannot send/receive war proposals) ---
    private final Map<UUID, Long> graceUntilByCiv = new HashMap<>();

    // --- Rematch: ordered pair key -> until epoch ms ---
    // key = min(a,b) + "_" + max(a,b) as UUID strings
    private final Map<String, Long> rematchUntilByPair = new HashMap<>();

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

    /* ---------------- TAX API (used by WarTaxUtil) ---------------- */

    public UUID getTaxWinnerCivId(UUID loserCivId) {
        return loserCivId == null ? null : taxWinnerByLoser.get(loserCivId);
    }

    public int getTaxBps(UUID loserCivId) {
        if (loserCivId == null) return 0;
        return taxBpsByLoser.getOrDefault(loserCivId, 0);
    }

    public long getTaxUntilMs(UUID loserCivId) {
        if (loserCivId == null) return 0L;
        return taxUntilByLoser.getOrDefault(loserCivId, 0L);
    }

    public void clearTax(UUID loserCivId) {
        if (loserCivId == null) return;
        taxWinnerByLoser.remove(loserCivId);
        taxBpsByLoser.remove(loserCivId);
        taxUntilByLoser.remove(loserCivId);
        setDirty();
    }

    /**
     * Set production tax on loser -> winner.
     * durationMs is a DURATION (not an absolute timestamp).
     */
    public void setTax(UUID loserCivId, UUID winnerCivId, int bps, long durationMs) {
        if (loserCivId == null) return;
        if (winnerCivId == null) { clearTax(loserCivId); return; }
        if (bps <= 0) { clearTax(loserCivId); return; }

        long until = System.currentTimeMillis() + Math.max(0L, durationMs);

        taxWinnerByLoser.put(loserCivId, winnerCivId);
        taxBpsByLoser.put(loserCivId, bps);
        taxUntilByLoser.put(loserCivId, until);
        setDirty();
    }

    /* ---------------- GRACE API ---------------- */

    public void setGrace(UUID civId, long untilEpochMs) {
        if (civId == null) return;
        if (untilEpochMs <= 0L) graceUntilByCiv.remove(civId);
        else graceUntilByCiv.put(civId, untilEpochMs);
        setDirty();
    }

    public boolean inGrace(UUID civId, long nowEpochMs) {
        if (civId == null) return false;
        long until = graceUntilByCiv.getOrDefault(civId, 0L);
        if (until <= 0L) return false;
        if (nowEpochMs > until) {
            graceUntilByCiv.remove(civId);
            setDirty();
            return false;
        }
        return true;
    }

    /* ---------------- REMATCH API ---------------- */

    public void setRematch(UUID civA, UUID civB, long untilEpochMs) {
        if (civA == null || civB == null) return;
        String key = pairKey(civA, civB);
        if (untilEpochMs <= 0L) rematchUntilByPair.remove(key);
        else rematchUntilByPair.put(key, untilEpochMs);
        setDirty();
    }

    public boolean inRematchCooldown(UUID civA, UUID civB, long nowEpochMs) {
        if (civA == null || civB == null) return false;
        String key = pairKey(civA, civB);
        long until = rematchUntilByPair.getOrDefault(key, 0L);
        if (until <= 0L) return false;
        if (nowEpochMs > until) {
            rematchUntilByPair.remove(key);
            setDirty();
            return false;
        }
        return true;
    }

    private static String pairKey(UUID a, UUID b) {
        // Stable ordering so (A,B) == (B,A)
        if (a.compareTo(b) <= 0) return a + "_" + b;
        return b + "_" + a;
    }

    /* ---------------- NBT ---------------- */

    public static WarStatusSavedData load(CompoundTag root, HolderLookup.Provider provider) {
        WarStatusSavedData data = new WarStatusSavedData();

        // Tax entries
        ListTag taxList = root.getList("TaxEntries", 10);
        for (int i = 0; i < taxList.size(); i++) {
            CompoundTag t = taxList.getCompound(i);
            if (!t.hasUUID("Loser")) continue;
            UUID loser = t.getUUID("Loser");

            UUID winner = t.hasUUID("Winner") ? t.getUUID("Winner") : null;
            int bps = t.getInt("Bps");
            long until = t.getLong("Until");

            if (winner != null && bps > 0) {
                data.taxWinnerByLoser.put(loser, winner);
                data.taxBpsByLoser.put(loser, bps);
                data.taxUntilByLoser.put(loser, until);
            }
        }

        // Grace entries
        ListTag graceList = root.getList("GraceEntries", 10);
        for (int i = 0; i < graceList.size(); i++) {
            CompoundTag t = graceList.getCompound(i);
            if (!t.hasUUID("Civ")) continue;
            UUID civ = t.getUUID("Civ");
            long until = t.getLong("Until");
            if (until > 0L) data.graceUntilByCiv.put(civ, until);
        }

        // Rematch entries
        ListTag rematchList = root.getList("RematchEntries", 10);
        for (int i = 0; i < rematchList.size(); i++) {
            CompoundTag t = rematchList.getCompound(i);
            String key = t.getString("Key");
            long until = t.getLong("Until");
            if (key != null && !key.isEmpty() && until > 0L) {
                data.rematchUntilByPair.put(key, until);
            }
        }

        return data;
    }

    @Override
    public CompoundTag save(CompoundTag root, HolderLookup.Provider provider) {
        // Tax
        ListTag taxList = new ListTag();
        for (UUID loser : taxWinnerByLoser.keySet()) {
            UUID winner = taxWinnerByLoser.get(loser);
            int bps = taxBpsByLoser.getOrDefault(loser, 0);
            long until = taxUntilByLoser.getOrDefault(loser, 0L);
            if (winner == null || bps <= 0) continue;

            CompoundTag t = new CompoundTag();
            t.putUUID("Loser", loser);
            t.putUUID("Winner", winner);
            t.putInt("Bps", bps);
            t.putLong("Until", until);
            taxList.add(t);
        }
        root.put("TaxEntries", taxList);

        // Grace
        ListTag graceList = new ListTag();
        for (Map.Entry<UUID, Long> e : graceUntilByCiv.entrySet()) {
            if (e.getValue() == null || e.getValue() <= 0L) continue;
            CompoundTag t = new CompoundTag();
            t.putUUID("Civ", e.getKey());
            t.putLong("Until", e.getValue());
            graceList.add(t);
        }
        root.put("GraceEntries", graceList);

        // Rematch
        ListTag rematchList = new ListTag();
        for (Map.Entry<String, Long> e : rematchUntilByPair.entrySet()) {
            if (e.getKey() == null || e.getKey().isEmpty()) continue;
            if (e.getValue() == null || e.getValue() <= 0L) continue;
            CompoundTag t = new CompoundTag();
            t.putString("Key", e.getKey());
            t.putLong("Until", e.getValue());
            rematchList.add(t);
        }
        root.put("RematchEntries", rematchList);

        return root;
    }
}
