package net.reminitous.mineciv.war;

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
    private long preparationEndsAtMs;
    private long warEndsAtMs;
    private long defenderLeaderSeenAtMs;

    public long defenderLeaderSeenAtMs() { return defenderLeaderSeenAtMs; }
    public void setDefenderLeaderSeenAtMs(long v) { this.defenderLeaderSeenAtMs = v; }

    private int preparationMinutes;

    private boolean defenderAccepted;

    public WarState(UUID warId) {
        this.warId = warId;
    }

    public UUID warId() { return warId; }

    public UUID attackerCivId() { return attackerCivId; }
    public UUID defenderCivId() { return defenderCivId; }

    public void setAttackerCivId(UUID v) { this.attackerCivId = v; }
    public void setDefenderCivId(UUID v) { this.defenderCivId = v; }

    public Phase phase() { return phase; }
    public void setPhase(Phase p) { this.phase = p == null ? Phase.PROPOSED : p; }

    public long proposedAtMs() { return proposedAtMs; }
    public void setProposedAtMs(long v) { this.proposedAtMs = v; }

    public long preparationEndsAtMs() { return preparationEndsAtMs; }
    public void setPreparationEndsAtMs(long v) { this.preparationEndsAtMs = v; }

    public long warEndsAtMs() { return warEndsAtMs; }
    public void setWarEndsAtMs(long v) { this.warEndsAtMs = v; }

    public int preparationMinutes() { return preparationMinutes; }
    public void setPreparationMinutes(int v) { this.preparationMinutes = Math.max(0, v); }

    public boolean defenderAccepted() { return defenderAccepted; }
    public void setDefenderAccepted(boolean v) { this.defenderAccepted = v; }

    /* ---------------- NBT ---------------- */

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();

        tag.putUUID("WarId", warId);

        if (attackerCivId != null) tag.putUUID("AttackerCivId", attackerCivId);
        if (defenderCivId != null) tag.putUUID("DefenderCivId", defenderCivId);

        tag.putString("Phase", phase.name());

        tag.putLong("ProposedAtMs", proposedAtMs);
        tag.putLong("PreparationEndsAtMs", preparationEndsAtMs);
        tag.putLong("WarEndsAtMs", warEndsAtMs);

        tag.putInt("PreparationMinutes", preparationMinutes);

        tag.putBoolean("DefenderAccepted", defenderAccepted);

        tag.putLong("DefenderLeaderSeenAtMs", defenderLeaderSeenAtMs);

        return tag;
    }

    public static WarState fromNbt(CompoundTag tag) {
        UUID warId = tag.getUUID("WarId");
        WarState w = new WarState(warId);

        if (tag.hasUUID("AttackerCivId")) w.attackerCivId = tag.getUUID("AttackerCivId");
        if (tag.hasUUID("DefenderCivId")) w.defenderCivId = tag.getUUID("DefenderCivId");

        String p = tag.getString("Phase");
        try {
            w.phase = Phase.valueOf(p);
        } catch (Exception ignored) {
            w.phase = Phase.PROPOSED;
        }

        w.proposedAtMs = tag.getLong("ProposedAtMs");
        w.preparationEndsAtMs = tag.getLong("PreparationEndsAtMs");
        w.warEndsAtMs = tag.getLong("WarEndsAtMs");

        w.preparationMinutes = tag.getInt("PreparationMinutes");

        w.defenderAccepted = tag.getBoolean("DefenderAccepted");

        w.defenderLeaderSeenAtMs = tag.getLong("DefenderLeaderSeenAtMs");

        return w;
    }
}
