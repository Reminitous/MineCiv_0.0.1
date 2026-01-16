package net.reminitous.mineciv.war;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class WarState {

    public enum Phase {
        PROPOSED,
        PREPARING,
        ACTIVE,
        ENDED
    }

    private final UUID warId;

    private UUID attackerCivId;
    private UUID defenderCivId;

    private Phase phase = Phase.PROPOSED;

    private long proposedAtMs;
    private int preparationMinutes;

    private boolean defenderAccepted;

    /**
     * Used for multiple policies:
     * - In PREPARING: end-of-prep timestamp
     * - In PROPOSED: "force start" timestamp (72h cap or 24h after decline)
     */
    private long preparationEndsAtMs;

    /**
     * When defender leader logs in, if they don't respond within 1 hour, war starts.
     * 0 means not set yet.
     */
    private long leaderOnlineDeadlineMs;

    /**
     * Bitmask for countdown warnings (persisted).
     * bit0 = 30m sent
     * bit1 = 10m sent
     * bit2 = 5m sent
     * bit3 = 1m sent
     */
    private int leaderWarnMask;

    public WarState(UUID warId) {
        this.warId = warId;
    }

    public UUID warId() { return warId; }

    public UUID attackerCivId() { return attackerCivId; }
    public void setAttackerCivId(UUID attackerCivId) { this.attackerCivId = attackerCivId; }

    public UUID defenderCivId() { return defenderCivId; }
    public void setDefenderCivId(UUID defenderCivId) { this.defenderCivId = defenderCivId; }

    public Phase phase() { return phase; }
    public void setPhase(Phase phase) { this.phase = phase; }

    public long proposedAtMs() { return proposedAtMs; }
    public void setProposedAtMs(long proposedAtMs) { this.proposedAtMs = proposedAtMs; }

    public int preparationMinutes() { return preparationMinutes; }
    public void setPreparationMinutes(int preparationMinutes) { this.preparationMinutes = preparationMinutes; }

    public boolean defenderAccepted() { return defenderAccepted; }
    public void setDefenderAccepted(boolean defenderAccepted) { this.defenderAccepted = defenderAccepted; }

    public long preparationEndsAtMs() { return preparationEndsAtMs; }
    public void setPreparationEndsAtMs(long preparationEndsAtMs) { this.preparationEndsAtMs = preparationEndsAtMs; }

    public long leaderOnlineDeadlineMs() { return leaderOnlineDeadlineMs; }
    public void setLeaderOnlineDeadlineMs(long leaderOnlineDeadlineMs) { this.leaderOnlineDeadlineMs = leaderOnlineDeadlineMs; }

    public int leaderWarnMask() { return leaderWarnMask; }
    public void setLeaderWarnMask(int leaderWarnMask) { this.leaderWarnMask = leaderWarnMask; }

    /* ---------------- NBT ---------------- */

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putUUID("WarId", warId);

        if (attackerCivId != null) t.putUUID("Attacker", attackerCivId);
        if (defenderCivId != null) t.putUUID("Defender", defenderCivId);

        t.putString("Phase", phase.name());
        t.putLong("ProposedAtMs", proposedAtMs);
        t.putInt("PrepMinutes", preparationMinutes);
        t.putBoolean("DefAccepted", defenderAccepted);
        t.putLong("PrepEndsAtMs", preparationEndsAtMs);

        t.putLong("LeaderOnlineDeadlineMs", leaderOnlineDeadlineMs);
        t.putInt("LeaderWarnMask", leaderWarnMask);

        return t;
    }

    public static WarState fromNbt(CompoundTag t, HolderLookup.Provider provider) {
        UUID warId = t.getUUID("WarId");
        WarState w = new WarState(warId);

        if (t.hasUUID("Attacker")) w.attackerCivId = t.getUUID("Attacker");
        if (t.hasUUID("Defender")) w.defenderCivId = t.getUUID("Defender");

        String phaseStr = t.getString("Phase");
        try {
            w.phase = Phase.valueOf(phaseStr);
        } catch (Exception ignored) {
            w.phase = Phase.PROPOSED;
        }

        w.proposedAtMs = t.getLong("ProposedAtMs");
        w.preparationMinutes = t.getInt("PrepMinutes");
        w.defenderAccepted = t.getBoolean("DefAccepted");
        w.preparationEndsAtMs = t.getLong("PrepEndsAtMs");

        w.leaderOnlineDeadlineMs = t.getLong("LeaderOnlineDeadlineMs");
        w.leaderWarnMask = t.getInt("LeaderWarnMask");

        return w;
    }
}
