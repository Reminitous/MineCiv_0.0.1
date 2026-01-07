package net.reminitous.mineciv.war;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class WarTickEvents {

    private WarTickEvents() {}

    // How often to scan (ticks). 20 = 1 sec. 100 = 5 sec.
    private static final int SCAN_EVERY_TICKS = 40; // every 2 seconds
    private static int tickCounter = 0;

    private static final long ONE_HOUR_MS = 60L * 60L * 1000L;
    private static final long SEVENTY_TWO_HOURS_MS = 72L * 60L * 60L * 1000L;
    private static final long DECLINE_START_MS = 24L * 60L * 60L * 1000L;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < SCAN_EVERY_TICKS) return;
        tickCounter = 0;

        var server = e.getServer();
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        WarSavedData warData = WarSavedData.get(server);
        CivSavedData civData = CivSavedData.get(server);

        long now = System.currentTimeMillis();

        // Copy list to avoid concurrent modification if anything removes/updates wars
        List<WarState> wars = new ArrayList<>(warData.wars().values());

        for (WarState war : wars) {
            if (war == null) continue;

            switch (war.phase()) {
                case PROPOSED -> tickProposed(overworld, civData, warData, war, now);
                case PREPARING -> tickPreparing(overworld, civData, warData, war, now);
                case ACTIVE -> tickActive(overworld, civData, warData, war, now);
                case ENDED -> {
                    // nothing for now
                }
            }
        }
    }

    /* ---------------- Phase ticks ---------------- */

    private static void tickProposed(ServerLevel level, CivSavedData civData, WarSavedData warData, WarState war, long now) {
        UUID defenderCivId = war.defenderCivId();
        UUID attackerCivId = war.attackerCivId();
        if (defenderCivId == null || attackerCivId == null) return;

        Civilization defender = civData.getCiv(defenderCivId);
        Civilization attacker = civData.getCiv(attackerCivId);
        if (defender == null || attacker == null) return;

        // Hard cap: if 72 hours from proposal -> start war
        if (now >= war.proposedAtMs() + SEVENTY_TWO_HOURS_MS) {
            forceStartFromProposed(level, attacker, defender, warData, war, now, "Defender did not respond (72h cap).");
            return;
        }

        // If defender declined via packet, we already move to PREPARING with 24h timer.
        // So here we only handle "no response" rule.

        UUID defLeaderId = defender.leader();
        if (defLeaderId == null) return;

        ServerPlayer defLeader = level.getServer().getPlayerList().getPlayer(defLeaderId);

        // If leader is online, start the "1 hour" timer when first seen.
        if (defLeader != null) {
            if (war.defenderLeaderSeenAtMs() == 0L) {
                war.setDefenderLeaderSeenAtMs(now);
                warData.putWar(war);
                return;
            }

            // If 1 hour passed since leader seen online -> start war
            if (now >= war.defenderLeaderSeenAtMs() + ONE_HOUR_MS) {
                forceStartFromProposed(level, attacker, defender, warData, war, now, "Defender leader did not respond within 1 hour.");
            }
        }
        // If leader is offline, do nothing here; when they come online later, we start the 1h timer then.
    }

    private static void tickPreparing(ServerLevel level, CivSavedData civData, WarSavedData warData, WarState war, long now) {
        UUID defenderCivId = war.defenderCivId();
        UUID attackerCivId = war.attackerCivId();
        if (defenderCivId == null || attackerCivId == null) return;

        Civilization defender = civData.getCiv(defenderCivId);
        Civilization attacker = civData.getCiv(attackerCivId);
        if (defender == null || attacker == null) return;

        long prepEnds = war.preparationEndsAtMs();
        if (prepEnds <= 0) return;

        if (now >= prepEnds) {
            startActive(level, attacker, defender, warData, war, now);
        }
    }

    private static void tickActive(ServerLevel level, CivSavedData civData, WarSavedData warData, WarState war, long now) {
        // We’ll add:
        // - war duration end
        // - health-based end
        // - spoils + cooldowns
        // in the next steps.

        // Placeholder: keep ACTIVE running.
    }

    /* ---------------- Transitions ---------------- */

    private static void forceStartFromProposed(ServerLevel level,
                                               Civilization attacker,
                                               Civilization defender,
                                               WarSavedData warData,
                                               WarState war,
                                               long now,
                                               String reason) {

        // Move to PREPARING but with immediate start (prepEnds=now)
        war.setDefenderAccepted(false);
        war.setPhase(WarState.Phase.PREPARING);
        war.setPreparationEndsAtMs(now);

        warData.putWar(war);

        notifyCiv(level, attacker, Component.literal("⚔ War is starting now! (" + reason + ")"));
        notifyCiv(level, defender, Component.literal("⚔ War is starting now! (" + reason + ")"));
    }

    private static void startActive(ServerLevel level,
                                    Civilization attacker,
                                    Civilization defender,
                                    WarSavedData warData,
                                    WarState war,
                                    long now) {

        war.setPhase(WarState.Phase.ACTIVE);
        // warEndsAtMs will be set later when we decide duration rules
        warData.putWar(war);

        notifyCiv(level, attacker, Component.literal("⚔ WAR HAS BEGUN!"));
        notifyCiv(level, defender, Component.literal("⚔ WAR HAS BEGUN!"));
    }

    private static void notifyCiv(ServerLevel level, Civilization civ, Component msg) {
        for (UUID memberId : civ.members()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) p.sendSystemMessage(msg);
        }
    }
}
