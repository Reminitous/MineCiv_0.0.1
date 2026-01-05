package net.reminitous.mineciv.war;

import net.minecraft.nbt.CompoundTag;

import java.util.UUID;

public final class WarRecord {

    public final UUID warId;
    public final UUID attackerCiv;
    public final UUID defenderCiv;

    public WarState state;

    public long proposedAtMs;
    public long prepMinutes;           // 15/30/45/60
    public long scheduledStartAtMs;    // when war becomes ACTIVE

    public boolean defenderDeclined;   // if declined, war still starts 24h later
    public long forceStartAtMs;        // the earliest forced start time per your policy

    public WarRecord(UUID warId, UUID attackerCiv, UUID defenderCiv) {
        this.warId = warId;
        this.attackerCiv = attackerCiv;
        this.defenderCiv = defenderCiv;
        this.state = WarState.PROPOSED;
    }

    public CompoundTag toNbt() {
        CompoundTag t = new CompoundTag();
        t.putUUID("WarId", warId);
        t.putUUID("Attacker", attackerCiv);
        t.putUUID("Defender", defenderCiv);
        t.putString("State", state.name());
        t.putLong("ProposedAtMs", proposedAtMs);
        t.putLong("PrepMinutes", prepMinutes);
        t.putLong("ScheduledStartAtMs", scheduledStartAtMs);
        t.putBoolean("DefenderDeclined", defenderDeclined);
        t.putLong("ForceStartAtMs", forceStartAtMs);
        return t;
    }

    public static WarRecord fromNbt(CompoundTag t) {
        UUID warId = t.getUUID("WarId");
        UUID a = t.getUUID("Attacker");
        UUID d = t.getUUID("Defender");
        WarRecord wr = new WarRecord(warId, a, d);
        wr.state = WarState.valueOf(t.getString("State"));
        wr.proposedAtMs = t.getLong("ProposedAtMs");
        wr.prepMinutes = t.getLong("PrepMinutes");
        wr.scheduledStartAtMs = t.getLong("ScheduledStartAtMs");
        wr.defenderDeclined = t.getBoolean("DefenderDeclined");
        wr.forceStartAtMs = t.getLong("ForceStartAtMs");
        return wr;
    }
}
