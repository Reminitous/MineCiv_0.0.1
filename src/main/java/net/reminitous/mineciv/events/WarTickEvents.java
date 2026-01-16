package net.reminitous.mineciv.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.server.MinecraftServer;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.war.WarEndManager;
import net.reminitous.mineciv.war.WarHealthManager;
import net.reminitous.mineciv.war.WarSavedData;
import net.reminitous.mineciv.war.WarState;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class WarTickEvents {

    private static final int PERIOD_TICKS = 20;

    private WarTickEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        MinecraftServer server = e.getServer();
        if (server.getTickCount() % PERIOD_TICKS != 0) return;

        ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return;

        tick(overworld);
    }

    private static void tick(ServerLevel level) {
        WarSavedData warData = WarSavedData.get(level.getServer());
        CivSavedData civData = CivSavedData.get(level.getServer());

        long now = System.currentTimeMillis();

        for (WarState war : warData.wars().values()) {
            if (war == null) continue;
            if (war.phase() == WarState.Phase.ENDED) continue;

            UUID aId = war.attackerCivId();
            UUID dId = war.defenderCivId();
            if (aId == null || dId == null) continue;

            Civilization attacker = civData.getCiv(aId);
            Civilization defender = civData.getCiv(dId);
            if (attacker == null || defender == null) continue;

            // PREPARING -> ACTIVE when prep ends
            if (war.phase() == WarState.Phase.PREPARING) {
                if (now >= war.preparationEndsAtMs()) {
                    startActive(level, warData, war, attacker, defender);
                }
                continue;
            }

            // PROPOSED: countdown warnings + auto-start
            if (war.phase() == WarState.Phase.PROPOSED) {
                // warnings only apply if leader deadline exists and leader is online
                maybeSendLeaderCountdownWarnings(level, civData, warData, war, defender, now);

                long forceAt = war.preparationEndsAtMs();
                long leaderAt = war.leaderOnlineDeadlineMs();

                long earliest = 0L;
                if (forceAt > 0L) earliest = forceAt;
                if (leaderAt > 0L) earliest = (earliest == 0L) ? leaderAt : Math.min(earliest, leaderAt);

                if (earliest > 0L && now >= earliest) {
                    startActive(level, warData, war, attacker, defender);
                }
                continue;
            }

            // ACTIVE: ensure health exists, and end if defeated
            if (war.phase() == WarState.Phase.ACTIVE) {
                WarHealthManager.initializeIfMissing(level, war);
                WarEndManager.tryEndIfDefeated(level, war);
            }
        }
    }

    /**
     * Sends defender leader warnings at 30/10/5/1 minutes remaining.
     * Uses a persistent bitmask in WarState so messages are only sent once.
     */
    private static void maybeSendLeaderCountdownWarnings(ServerLevel level,
                                                         CivSavedData civData,
                                                         WarSavedData warData,
                                                         WarState war,
                                                         Civilization defender,
                                                         long now) {

        long deadline = war.leaderOnlineDeadlineMs();
        if (deadline <= 0L) return;

        long remainingMs = deadline - now;
        if (remainingMs <= 0L) return;

        UUID leaderId = defender.leader();
        if (leaderId == null) return;

        ServerPlayer leader = level.getServer().getPlayerList().getPlayer(leaderId);
        if (leader == null) return; // only warn while leader is online

        long remainingMinutes = (remainingMs + 59_999L) / 60_000L; // ceil minutes

        int mask = war.leaderWarnMask();

        // threshold -> bit index
        // 30m -> bit0, 10m -> bit1, 5m -> bit2, 1m -> bit3
        if (remainingMinutes <= 30 && (mask & 1) == 0) {
            leader.sendSystemMessage(Component.literal("⚠ War proposal: 30 minutes remaining to respond."));
            mask |= 1;
        }
        if (remainingMinutes <= 10 && (mask & 2) == 0) {
            leader.sendSystemMessage(Component.literal("⚠ War proposal: 10 minutes remaining to respond."));
            mask |= 2;
        }
        if (remainingMinutes <= 5 && (mask & 4) == 0) {
            leader.sendSystemMessage(Component.literal("⚠ War proposal: 5 minutes remaining to respond."));
            mask |= 4;
        }
        if (remainingMinutes <= 1 && (mask & 8) == 0) {
            leader.sendSystemMessage(Component.literal("⚠ War proposal: 1 minute remaining to respond."));
            mask |= 8;
        }

        if (mask != war.leaderWarnMask()) {
            war.setLeaderWarnMask(mask);
            warData.putWar(war);
        }
    }

    private static void startActive(ServerLevel level,
                                    WarSavedData warData,
                                    WarState war,
                                    Civilization attacker,
                                    Civilization defender) {

        war.setPhase(WarState.Phase.ACTIVE);
        warData.putWar(war);

        warData.setActiveWar(attacker.id(), war.warId());
        warData.setActiveWar(defender.id(), war.warId());

        WarHealthManager.initializeIfMissing(level, war);

        notifyCiv(level, attacker, Component.literal("⚔ WAR HAS BEGUN against " + safeName(defender)));
        notifyCiv(level, defender, Component.literal("⚔ WAR HAS BEGUN against " + safeName(attacker)));
    }

    private static void notifyCiv(ServerLevel level, Civilization civ, Component msg) {
        for (UUID memberId : civ.members()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) p.sendSystemMessage(msg);
        }
    }

    private static String safeName(Civilization civ) {
        if (civ == null) return "Unknown";
        String n = civ.name();
        return (n == null || n.isBlank()) ? civ.id().toString() : n;
    }
}
