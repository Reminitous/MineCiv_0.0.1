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

    // How often to scan (ticks). 20 = 1 sec. 40 = 2 sec.
    private static final int SCAN_EVERY_TICKS = 40;
    private static int tickCounter = 0;

    private static final long ONE_HOUR_MS = 60L * 60L * 1000L;
    private static final long SEVENTY_TWO_HOURS_MS = 72L * 60L * 60L * 1000L;

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

        // Copy list to avoid concurrent modification if anything updates wars in-place
        List<WarState> wars = new ArrayList<>(warData.wars().values());

        for (WarState war : wars) {
            if (war == null) continue;

            switch (war.phase()) {
                case PROPOSED -> tickProposed(overworld, civData, warData, war, now);
                case PREPARING -> tickPreparing(overworld, civData, warData, war, now);
                case ACTIVE -> tickActive(overworld, civData, warData, war, now);
                case ENDED -> {
                    // nothing
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

        long proposedAt = war.proposedAtMs();
        if (proposedAt <= 0L) {
            war.setProposedAtMs(now);
            warData.putWar(war);
            proposedAt = now;
        }

        // 72 hour hard cap
        if (now >= proposedAt + SEVENTY_TWO_HOURS_MS) {
            forceStartFromProposed(level, attacker, defender, warData, war, now,
                    "Defender did not respond (72h cap).");
            return;
        }

        // Decline rule (24h after decline) is stored in preparationEndsAtMs while still PROPOSED in your decline packet.
        long forceStartAt = war.preparationEndsAtMs(); // used as "force start" while PROPOSED
        if (forceStartAt > 0L && now >= forceStartAt) {
            forceStartFromProposed(level, attacker, defender, warData, war, now,
                    "War auto-started after decline/no response timer.");
            return;
        }

        // 1-hour leader online deadline:
        // when defender leader is online, we set leaderOnlineDeadlineMs once, then auto-start when reached.
        UUID defLeaderId = defender.leader();
        if (defLeaderId == null) return;

        ServerPlayer defLeader = level.getServer().getPlayerList().getPlayer(defLeaderId);
        if (defLeader == null) {
            // Leader offline -> do nothing until they come online
            return;
        }

        long deadline = war.leaderOnlineDeadlineMs();
        if (deadline <= 0L) {
            // Start the 1-hour window now
            war.setLeaderOnlineDeadlineMs(now + ONE_HOUR_MS);
            war.setLeaderWarnMask(0);
            warData.putWar(war);

            notifyCiv(level, attacker, Component.literal("⚔ Defender leader is online. War will auto-start in 60 minutes if not answered."));
            notifyCiv(level, defender, Component.literal("⚔ You have 60 minutes to accept/decline this war at your monument."));
            return;
        }

        if (now >= deadline) {
            forceStartFromProposed(level, attacker, defender, warData, war, now,
                    "Defender leader did not respond within 1 hour.");
        }
    }

    private static void tickPreparing(ServerLevel level, CivSavedData civData, WarSavedData warData, WarState war, long now) {
        UUID defenderCivId = war.defenderCivId();
        UUID attackerCivId = war.attackerCivId();
        if (defenderCivId == null || attackerCivId == null) return;

        Civilization defender = civData.getCiv(defenderCivId);
        Civilization attacker = civData.getCiv(attackerCivId);
        if (defender == null || attacker == null) return;

        long prepEnds = war.preparationEndsAtMs();
        if (prepEnds <= 0L) return;

        if (now >= prepEnds) {
            startActive(level, attacker, defender, warData, war);
        }
    }

    private static void tickActive(ServerLevel level, CivSavedData civData, WarSavedData warData, WarState war, long now) {
        // Ensure health exists + check if anyone is defeated
        WarHealthManager.initializeIfMissing(level, war);
        WarEndManager.tryEndIfDefeated(level, war);
    }

    /* ---------------- Transitions ---------------- */

    private static void forceStartFromProposed(ServerLevel level,
                                               Civilization attacker,
                                               Civilization defender,
                                               WarSavedData warData,
                                               WarState war,
                                               long now,
                                               String reason) {

        // PROPOSED -> PREPARING but immediate start
        war.setDefenderAccepted(false);
        war.setPhase(WarState.Phase.PREPARING);
        war.setPreparationEndsAtMs(now);

        // Clear leader deadline now that we're starting
        war.setLeaderOnlineDeadlineMs(0L);
        war.setLeaderWarnMask(0);

        warData.putWar(war);

        notifyCiv(level, attacker, Component.literal("⚔ War is starting now! (" + reason + ")"));
        notifyCiv(level, defender, Component.literal("⚔ War is starting now! (" + reason + ")"));
    }

    private static void startActive(ServerLevel level,
                                    Civilization attacker,
                                    Civilization defender,
                                    WarSavedData warData,
                                    WarState war) {

        war.setPhase(WarState.Phase.ACTIVE);

        // Clear proposal timers
        war.setLeaderOnlineDeadlineMs(0L);
        war.setLeaderWarnMask(0);

        warData.putWar(war);

        WarHealthManager.initializeIfMissing(level, war);

        notifyCiv(level, attacker, Component.literal("⚔ WAR HAS BEGUN!"));
        notifyCiv(level, defender, Component.literal("⚔ WAR HAS BEGUN!"));
    }

    private static void notifyCiv(ServerLevel level, Civilization civ, Component msg) {
        if (civ == null) return;
        for (UUID memberId : civ.members()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) p.sendSystemMessage(msg);
        }
    }
}
