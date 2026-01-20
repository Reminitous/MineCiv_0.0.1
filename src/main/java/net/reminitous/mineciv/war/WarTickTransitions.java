package net.reminitous.mineciv.war;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.UUID;

public final class WarTickTransitions {

    private WarTickTransitions() {}

    // Policy knobs
    private static final long ONE_HOUR_MS = 60L * 60L * 1000L;
    private static final long TWENTY_FOUR_HOURS_MS = 24L * 60L * 60L * 1000L;
    private static final long SEVENTY_TWO_HOURS_MS = 72L * 60L * 60L * 1000L;

    /** Call once per server tick (or once per second, either works). */
    public static void tick(ServerLevel level) {
        if (level == null) return;
        MinecraftServer server = level.getServer();
        if (server == null) return;

        WarSavedData warData = WarSavedData.get(server);
        CivSavedData civData = CivSavedData.get(server);

        long now = System.currentTimeMillis();

        for (WarState war : warData.wars().values()) {
            if (war == null) continue;

            // Already ended? Ignore.
            if (war.phase() == WarState.Phase.ENDED) continue;

            // ---- ACTIVE: initialize health + check end ----
            if (war.phase() == WarState.Phase.ACTIVE) {
                WarHealthManager.initializeIfMissing(level, war);
                WarEndManager.tryEndIfDefeated(level, war);
                continue;
            }

            // ---- PREPARING: when timer ends -> ACTIVE ----
            if (war.phase() == WarState.Phase.PREPARING) {
                long prepEnds = war.preparationEndsAtMs();
                if (prepEnds > 0L && now >= prepEnds) {
                    becomeActive(level, warData, civData, war);
                }
                continue;
            }

            // ---- PROPOSED: enforce leader-online 1h + 72h hard cap + decline 24h force ----
            if (war.phase() == WarState.Phase.PROPOSED) {
                UUID attackerCivId = war.attackerCivId();
                UUID defenderCivId = war.defenderCivId();
                if (attackerCivId == null || defenderCivId == null) continue;

                long proposedAt = war.proposedAtMs();
                if (proposedAt <= 0L) {
                    // If missing, treat "now" as proposal time so we don't softlock
                    proposedAt = now;
                    war.setProposedAtMs(proposedAt);
                    warData.putWar(war);
                }

                long hardCap72 = proposedAt + SEVENTY_TWO_HOURS_MS;

                // (A) Decline / force-start time
                // You already use preparationEndsAtMs as the "force start" while still PROPOSED.
                long forceStart = war.preparationEndsAtMs();
                // If it's unset, default to 72h hard cap (not 24h).
                if (forceStart <= 0L) forceStart = hardCap72;

                // Always respect the earliest of (forceStart, hardCap72)
                long earliest = Math.min(forceStart, hardCap72);

                // (B) 1-hour leader online deadline logic
                // If defender leader is online and we haven't started the timer, start it now.
                long leaderDeadline = war.leaderOnlineDeadlineMs();

                Civilization defender = civData.getCiv(defenderCivId);
                UUID defLeaderId = (defender == null) ? null : defender.leader();

                boolean leaderOnline = false;
                if (defLeaderId != null) {
                    ServerPlayer defLeader = server.getPlayerList().getPlayer(defLeaderId);
                    leaderOnline = (defLeader != null);
                }

                if (leaderOnline && leaderDeadline <= 0L) {
                    leaderDeadline = now + ONE_HOUR_MS;
                    war.setLeaderOnlineDeadlineMs(leaderDeadline);
                    war.setLeaderWarnMask(0); // reset warnings for the new timer
                    warData.putWar(war);

                    // Notify both civs once when timer begins
                    broadcastToCiv(level, civData.getCiv(attackerCivId),
                            Component.literal("⚔ Defender leader is online. War will auto-start in 60 minutes if not answered."));
                    broadcastToCiv(level, defender,
                            Component.literal("⚔ You have 60 minutes to accept/decline this war at your monument."));
                }

                // If leader deadline exists, it becomes another “force start” candidate.
                if (leaderDeadline > 0L) earliest = Math.min(earliest, leaderDeadline);

                // If time reached -> ACTIVE (no prep)
                if (now >= earliest) {
                    becomeActive(level, warData, civData, war);
                }
            }
        }
    }

    private static void becomeActive(ServerLevel level, WarSavedData warData, CivSavedData civData, WarState war) {
        UUID a = war.attackerCivId();
        UUID d = war.defenderCivId();
        if (a == null || d == null) return;

        // Transition
        war.setPhase(WarState.Phase.ACTIVE);
        // Clear proposal timers
        war.setLeaderOnlineDeadlineMs(0L);
        war.setLeaderWarnMask(0);
        // No longer using prepEnds for PROPOSED/DECLINED state
        // (but we keep whatever value is there; it no longer matters in ACTIVE)

        warData.putWar(war);

        // ACTIVE mapping should only be set now
        warData.setActiveWar(a, war.warId());
        warData.setActiveWar(d, war.warId());

        // Make sure pending mapping still points to this war (safe)
        warData.setPendingWar(a, war.warId());
        warData.setPendingWar(d, war.warId());

        // Initialize war health snapshot once
        WarHealthManager.initializeIfMissing(level, war);

        Civilization attacker = civData.getCiv(a);
        Civilization defender = civData.getCiv(d);

        String aName = safeName(attacker, a);
        String dName = safeName(defender, d);

        Component startMsg = Component.literal("⚔ WAR STARTED: " + aName + " vs " + dName);

        broadcastToCiv(level, attacker, startMsg);
        broadcastToCiv(level, defender, startMsg);
    }

    private static void broadcastToCiv(ServerLevel level, Civilization civ, Component msg) {
        if (civ == null) return;
        for (UUID memberId : civ.members()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) p.sendSystemMessage(msg);
        }
    }

    private static String safeName(Civilization civ, UUID id) {
        if (civ != null && civ.name() != null && !civ.name().isBlank()) return civ.name();
        if (id == null) return "Unknown";
        String s = id.toString();
        return s.substring(0, 8);
    }
}
