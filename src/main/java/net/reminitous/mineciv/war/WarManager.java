package net.reminitous.mineciv.war;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;

import java.util.Optional;
import java.util.UUID;

public final class WarManager {

    private WarManager() {}

    public static Optional<WarState> activeWarBetween(ServerLevel level, UUID a, UUID b) {
        WarSavedData warData = WarSavedData.get(level.getServer());

        for (WarState w : warData.wars().values()) {
            if (w.phase() != WarState.Phase.ACTIVE) continue;

            boolean match = (equals(w.attackerCivId(), a) && equals(w.defenderCivId(), b))
                    || (equals(w.attackerCivId(), b) && equals(w.defenderCivId(), a));

            if (match) return Optional.of(w);
        }
        return Optional.empty();
    }

    public static boolean proposeWar(ServerLevel level, UUID attackerCivId, UUID defenderCivId, int prepMinutes) {
        if (attackerCivId == null || defenderCivId == null) return false;
        if (attackerCivId.equals(defenderCivId)) return false;
        if (!(prepMinutes == 15 || prepMinutes == 30 || prepMinutes == 45 || prepMinutes == 60)) return false;

        CivSavedData civData = CivSavedData.get(level.getServer());
        Civilization attacker = civData.getCiv(attackerCivId);
        Civilization defender = civData.getCiv(defenderCivId);
        if (attacker == null || defender == null) return false;

        // cannot war allies
        if (CivilizationManager.areAllies(level, attackerCivId, defenderCivId)) return false;

        WarSavedData warData = WarSavedData.get(level.getServer());

        // either civ already in a war
        if (warData.getActiveWarId(attackerCivId) != null) return false;
        if (warData.getActiveWarId(defenderCivId) != null) return false;

        // create war
        long now = System.currentTimeMillis();

        WarState w = new WarState(UUID.randomUUID());
        w.setAttackerCivId(attackerCivId);
        w.setDefenderCivId(defenderCivId);
        w.setPhase(WarState.Phase.PROPOSED);
        w.setProposedAtMs(now);
        w.setPreparationMinutes(prepMinutes);
        w.setDefenderAccepted(false);

        warData.putWar(w);
        warData.setActiveWar(attackerCivId, w.warId());
        warData.setActiveWar(defenderCivId, w.warId());

        return true;
    }

    public static boolean acceptWar(ServerLevel level, UUID defenderCivId, UUID warId) {
        WarSavedData warData = WarSavedData.get(level.getServer());
        WarState w = warData.getWar(warId);
        if (w == null) return false;

        if (!equals(w.defenderCivId(), defenderCivId)) return false;
        if (w.phase() != WarState.Phase.PROPOSED) return false;

        long now = System.currentTimeMillis();
        long prepEnds = now + (long) w.preparationMinutes() * 60_000L;

        w.setDefenderAccepted(true);
        w.setPhase(WarState.Phase.PREPARING);
        w.setPreparationEndsAtMs(prepEnds);

        warData.putWar(w);
        return true;
    }

    public static boolean declineWar(ServerLevel level, UUID defenderCivId, UUID warId) {
        WarSavedData warData = WarSavedData.get(level.getServer());
        WarState w = warData.getWar(warId);
        if (w == null) return false;

        if (!equals(w.defenderCivId(), defenderCivId)) return false;
        if (w.phase() != WarState.Phase.PROPOSED) return false;

        // Decline -> still starts in 24 hours
        long now = System.currentTimeMillis();
        long startsIn24h = now + 24L * 60L * 60L * 1000L;

        w.setDefenderAccepted(false);
        w.setPhase(WarState.Phase.PREPARING);
        w.setPreparationEndsAtMs(startsIn24h);

        warData.putWar(w);
        return true;
    }

    public static Optional<Civilization> civOf(ServerLevel level, UUID playerId) {
        return CivilizationManager.findPlayerCiv(level, playerId);
    }

    public static boolean isLeader(ServerLevel level, UUID playerId) {
        Optional<Civilization> civ = civOf(level, playerId);
        return civ.isPresent() && civ.get().leader() != null && civ.get().leader().equals(playerId);
    }

    public static void notifyCiv(ServerLevel level, Civilization civ, net.minecraft.network.chat.Component msg) {
        for (UUID memberId : civ.members()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) p.sendSystemMessage(msg);
        }
    }

    private static boolean equals(UUID a, UUID b) {
        return a != null && a.equals(b);
    }
}
