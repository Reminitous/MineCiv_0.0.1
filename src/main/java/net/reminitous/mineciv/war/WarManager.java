package net.reminitous.mineciv.war;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.Optional;
import java.util.UUID;

public final class WarManager {

    private WarManager() {}

    public static Optional<WarRecord> activeWarBetween(CivSavedData data, UUID a, UUID b) {
        for (WarRecord wr : data.wars().values()) {
            if (wr.state != WarState.ACTIVE) continue;
            boolean match = (wr.attackerCiv.equals(a) && wr.defenderCiv.equals(b))
                    || (wr.attackerCiv.equals(b) && wr.defenderCiv.equals(a));
            if (match) return Optional.of(wr);
        }
        return Optional.empty();
    }

    public static boolean proposeWar(ServerLevel level, UUID attackerCiv, UUID defenderCiv, int prepMinutes) {
        if (attackerCiv == null || defenderCiv == null) return false;
        if (attackerCiv.equals(defenderCiv)) return false;
        if (!(prepMinutes == 15 || prepMinutes == 30 || prepMinutes == 45 || prepMinutes == 60)) return false;

        CivSavedData data = CivSavedData.get(level.getServer());

        if (activeWarBetween(data, attackerCiv, defenderCiv).isPresent()) return false;

        // create war record
        UUID warId = UUID.randomUUID();
        WarRecord wr = new WarRecord(warId, attackerCiv, defenderCiv);
        wr.proposedAtMs = System.currentTimeMillis();
        wr.prepMinutes = prepMinutes;

        // force start policy scaffolding:
        // - if declined: 24h after proposal
        // - if no response: if leader online, 1h; else when leader comes online; hard cap 72h
        // We'll compute "forceStartAtMs" as the 72h cap initially; updated later on tick.
        wr.forceStartAtMs = wr.proposedAtMs + 72L * 60L * 60L * 1000L;

        data.wars().put(wr.warId, wr);
        data.setDirty();
        return true;
    }

    public static boolean acceptWar(ServerLevel level, UUID defenderCiv, UUID warId) {
        CivSavedData data = CivSavedData.get(level.getServer());
        WarRecord wr = data.wars().get(warId);
        if (wr == null) return false;
        if (!wr.defenderCiv.equals(defenderCiv)) return false;
        if (wr.state != WarState.PROPOSED) return false;

        long now = System.currentTimeMillis();
        wr.state = WarState.SCHEDULED;
        wr.scheduledStartAtMs = now + wr.prepMinutes * 60L * 1000L;

        data.setDirty();
        return true;
    }

    public static boolean declineWar(ServerLevel level, UUID defenderCiv, UUID warId) {
        CivSavedData data = CivSavedData.get(level.getServer());
        WarRecord wr = data.wars().get(warId);
        if (wr == null) return false;
        if (!wr.defenderCiv.equals(defenderCiv)) return false;
        if (wr.state != WarState.PROPOSED) return false;

        wr.defenderDeclined = true;
        // starts anyway 24h after decline/proposal (we’ll use proposal time for simplicity)
        wr.forceStartAtMs = Math.min(wr.forceStartAtMs, wr.proposedAtMs + 24L * 60L * 60L * 1000L);

        data.setDirty();
        return true;
    }

    public static void tick(ServerLevel level) {
        CivSavedData data = CivSavedData.get(level.getServer());
        long now = System.currentTimeMillis();

        for (WarRecord wr : data.wars().values()) {
            if (wr.state == WarState.ENDED) continue;

            // If scheduled and time reached -> ACTIVE
            if (wr.state == WarState.SCHEDULED && now >= wr.scheduledStartAtMs) {
                wr.state = WarState.ACTIVE;
                data.setDirty();
                continue;
            }

            // If proposed and force time reached -> ACTIVE
            if (wr.state == WarState.PROPOSED && now >= wr.forceStartAtMs) {
                wr.state = WarState.ACTIVE;
                data.setDirty();
            }
        }
    }

    public static Optional<Civilization> civOf(ServerLevel level, UUID playerId) {
        return net.reminitous.mineciv.civ.CivilizationManager.findPlayerCiv(level, playerId);
    }

    public static boolean isLeader(ServerLevel level, UUID playerId) {
        Optional<Civilization> civ = civOf(level, playerId);
        return civ.isPresent() && civ.get().leader() != null && civ.get().leader().equals(playerId);
    }
}
