package net.reminitous.mineciv.war;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import net.minecraftforge.common.capabilities.ForgeCapabilities;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.UUID;

public final class WarHealthManager {

    // Tuning knobs (v1)
    public static final long MIN_START_HEALTH = 2500L; // so poor civs still have something
    public static final long DEATH_DAMAGE = 250L;
    public static final long BLOCK_BREAK_DAMAGE = 5L;

    private WarHealthManager() {}

    /** Called exactly once when war becomes ACTIVE. */
    public static void initializeIfMissing(ServerLevel level, WarState war) {
        if (war == null) return;
        if (war.phase() != WarState.Phase.ACTIVE) return;

        WarHealthSavedData hData = WarHealthSavedData.get(level.getServer());
        if (hData.get(war.warId()) != null) return; // already initialized

        UUID a = war.attackerCivId();
        UUID d = war.defenderCivId();
        if (a == null || d == null) return;

        CivSavedData civData = CivSavedData.get(level.getServer());
        Civilization attacker = civData.getCiv(a);
        Civilization defender = civData.getCiv(d);
        if (attacker == null || defender == null) return;

        long aValue = snapshotCivStorageValue(level, attacker);
        long dValue = snapshotCivStorageValue(level, defender);

        long aStart = Math.max(MIN_START_HEALTH, aValue);
        long dStart = Math.max(MIN_START_HEALTH, dValue);

        WarHealthSavedData.WarHealthRecord rec = new WarHealthSavedData.WarHealthRecord(war.warId());
        rec.attackerCivId = a;
        rec.defenderCivId = d;

        rec.attackerStartValue = aStart;
        rec.defenderStartValue = dStart;

        rec.attackerHealth = aStart;
        rec.defenderHealth = dStart;

        rec.createdAtMs = System.currentTimeMillis();

        hData.put(rec);
    }

    public static WarHealthSavedData.WarHealthRecord get(ServerLevel level, UUID warId) {
        return WarHealthSavedData.get(level.getServer()).get(warId);
    }

    public static void damageCiv(ServerLevel level, UUID warId, UUID civId, long amount) {
        if (amount <= 0) return;
        WarHealthSavedData hData = WarHealthSavedData.get(level.getServer());
        WarHealthSavedData.WarHealthRecord rec = hData.get(warId);
        if (rec == null) return;

        if (civId != null && civId.equals(rec.attackerCivId)) {
            rec.attackerHealth -= amount;
            hData.put(rec);
        } else if (civId != null && civId.equals(rec.defenderCivId)) {
            rec.defenderHealth -= amount;
            hData.put(rec);
        }
    }

    public static long deathDamage() { return DEATH_DAMAGE; }
    public static long blockBreakDamage() { return BLOCK_BREAK_DAMAGE; }

    /* ---------------- Snapshot logic ---------------- */

    private static long snapshotCivStorageValue(ServerLevel level, Civilization civ) {
        long sum = 0L;

        // Scan all claimed chunks owned by the civ (snapshot once at war start)
        for (long chunkLong : civ.claimedChunks()) {
            ChunkPos cp = new ChunkPos(chunkLong);

            // Force-load chunk reference (should already be loaded if players exist nearby)
            LevelChunk chunk = level.getChunk(cp.x, cp.z);

            // In 1.21.x this exists on LevelChunk; we iterate block-entity positions
            for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                BlockEntity be = chunk.getBlockEntity(bePos);
                if (be == null) continue;

                // Only count storage-like inventories (anything exposing ITEM_HANDLER)
                var cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
                if (!cap.isPresent()) continue;

                var inv = cap.orElse(null);
                if (inv == null) continue;

                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack st = inv.getStackInSlot(i);
                    if (st.isEmpty()) continue;
                    sum += stackValue(st);
                }
            }
        }

        return sum;
    }

    /** Simple v1 heuristic: count * rarity weight. Tune later with a real price table. */
    private static long stackValue(ItemStack st) {
        int count = st.getCount();
        Rarity r = st.getRarity();
        int w = switch (r) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 4;
            case EPIC -> 8;
        };
        return (long) count * w;
    }
}
