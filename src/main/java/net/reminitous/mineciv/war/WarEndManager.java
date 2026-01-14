package net.reminitous.mineciv.war;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.UUID;

public final class WarEndManager {

    private WarEndManager() {}

    public static void tryEndIfDefeated(ServerLevel level, WarState war) {
        if (war == null) return;
        if (war.phase() != WarState.Phase.ACTIVE) return;

        WarHealthSavedData.WarHealthRecord rec = WarHealthManager.get(level, war.warId());
        if (rec == null) return;

        boolean attackerLost = rec.attackerHealth <= 0;
        boolean defenderLost = rec.defenderHealth <= 0;

        if (!attackerLost && !defenderLost) return;

        UUID winner = attackerLost ? rec.defenderCivId : rec.attackerCivId;
        UUID loser  = attackerLost ? rec.attackerCivId : rec.defenderCivId;

        endWar(level, war.warId(), winner, loser);
    }

    public static void endWar(ServerLevel level, UUID warId, UUID winnerCivId, UUID loserCivId) {
        var server = level.getServer();

        WarSavedData warData = WarSavedData.get(server);
        WarState war = warData.getWar(warId);
        if (war == null) return;

        if (war.phase() == WarState.Phase.ENDED) return;

        // Mark ended
        war.setPhase(WarState.Phase.ENDED);
        warData.putWar(war);

        // Clear "active war" mapping for both civs
        if (war.attackerCivId() != null) warData.setActiveWar(war.attackerCivId(), null);
        if (war.defenderCivId() != null) warData.setActiveWar(war.defenderCivId(), null);

        // Remove health record (optional: keep for history later)
        WarHealthSavedData.get(server).remove(warId);

        CivSavedData civData = CivSavedData.get(server);
        Civilization winner = winnerCivId == null ? null : civData.getCiv(winnerCivId);
        Civilization loser  = loserCivId == null ? null : civData.getCiv(loserCivId);

        String wName = winner == null ? String.valueOf(winnerCivId) : (winner.name() == null ? String.valueOf(winnerCivId) : winner.name());
        String lName = loser  == null ? String.valueOf(loserCivId)  : (loser.name()  == null ? String.valueOf(loserCivId)  : loser.name());

        broadcastToCiv(level, winner, Component.literal("🏆 War ended! Winner: " + wName + " | Loser: " + lName));
        broadcastToCiv(level, loser,  Component.literal("💀 War ended! Winner: " + wName + " | Loser: " + lName));

        // Apply spoils + grace/rematch + tax status
        net.reminitous.mineciv.war.WarSpoilsManager.applySpoilsAndCooldowns(level, winnerCivId, loserCivId);

    }

    private static void broadcastToCiv(ServerLevel level, Civilization civ, Component msg) {
        if (civ == null) return;
        for (UUID memberId : civ.members()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) p.sendSystemMessage(msg);
        }
    }
}
