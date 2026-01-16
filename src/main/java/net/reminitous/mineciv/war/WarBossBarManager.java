package net.reminitous.mineciv.war;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.minecraft.world.BossEvent;
import net.minecraft.server.level.ServerBossEvent;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.*;

public final class WarBossBarManager {

    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();
    private static long lastUpdateTick = -9999;

    private WarBossBarManager() {}

    /** Call this from a server tick event; it self-throttles to once per second. */
    public static void tick(MinecraftServer server) {
        long nowTick = server.getTickCount();
        if (nowTick - lastUpdateTick < 20) return; // once per second
        lastUpdateTick = nowTick;

        WarSavedData warData = WarSavedData.get(server);
        CivSavedData civData = CivSavedData.get(server);
        WarHealthSavedData healthData = WarHealthSavedData.get(server);

        // Track which war bars should remain
        Set<UUID> stillValid = new HashSet<>();

        for (WarState war : warData.wars().values()) {
            if (war == null) continue;

            if (war.phase() != WarState.Phase.ACTIVE) continue;

            UUID warId = war.warId();
            if (warId == null) continue;

            WarHealthSavedData.WarHealthRecord rec = healthData.get(warId);
            if (rec == null) continue;

            UUID aId = war.attackerCivId();
            UUID dId = war.defenderCivId();
            if (aId == null || dId == null) continue;

            Civilization attacker = civData.getCiv(aId);
            Civilization defender = civData.getCiv(dId);

            String aName = (attacker != null && attacker.name() != null && !attacker.name().isBlank())
                    ? attacker.name() : aId.toString().substring(0, 8);
            String dName = (defender != null && defender.name() != null && !defender.name().isBlank())
                    ? defender.name() : dId.toString().substring(0, 8);

            // Make/update bossbar
            ServerBossEvent bar = BARS.computeIfAbsent(warId, k ->
                    new ServerBossEvent(Component.literal("War"),
                            BossEvent.BossBarColor.RED,
                            BossEvent.BossBarOverlay.PROGRESS)
            );

            long aHp = Math.max(0, rec.attackerHealth);
            long dHp = Math.max(0, rec.defenderHealth);
            long aStart = Math.max(1, rec.attackerStartValue);
            long dStart = Math.max(1, rec.defenderStartValue);

            float aPct = clamp01((float) aHp / (float) aStart);
            float dPct = clamp01((float) dHp / (float) dStart);

            // Single bar -> show "how close someone is to losing"
            float progress = Math.min(aPct, dPct);
            bar.setProgress(progress);

            bar.setName(Component.literal(
                    "⚔ " + aName + " " + aHp + "/" + aStart +
                            "  vs  " + dName + " " + dHp + "/" + dStart
            ));

            // Viewers: all online members of both civs
            Set<UUID> viewers = new HashSet<>();
            if (attacker != null) viewers.addAll(attacker.members());
            if (defender != null) viewers.addAll(defender.members());

            // Add/remove players to match current viewers
            // (We do it by scanning online players, then reconciling.)
            Set<UUID> onlineViewerIds = new HashSet<>();
            for (UUID pid : viewers) {
                ServerPlayer sp = server.getPlayerList().getPlayer(pid);
                if (sp != null) {
                    onlineViewerIds.add(pid);
                    if (!bar.getPlayers().contains(sp)) bar.addPlayer(sp);
                }
            }

            // Remove anyone currently on the bar who shouldn't see it
            // (Copy to avoid concurrent modification)
            List<ServerPlayer> current = new ArrayList<>(bar.getPlayers());
            for (ServerPlayer sp : current) {
                if (!onlineViewerIds.contains(sp.getUUID())) {
                    bar.removePlayer(sp);
                }
            }

            stillValid.add(warId);
        }

        // Remove bossbars for wars no longer ACTIVE (or missing health)
        Iterator<Map.Entry<UUID, ServerBossEvent>> it = BARS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, ServerBossEvent> e = it.next();
            UUID warId = e.getKey();
            ServerBossEvent bar = e.getValue();

            if (!stillValid.contains(warId)) {
                // Remove from all players and delete
                for (ServerPlayer sp : new ArrayList<>(bar.getPlayers())) {
                    bar.removePlayer(sp);
                }
                it.remove();
            }
        }
    }

    private static float clamp01(float v) {
        if (v < 0f) return 0f;
        if (v > 1f) return 1f;
        return v;
    }
}
