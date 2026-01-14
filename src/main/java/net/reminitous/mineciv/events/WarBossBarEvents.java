package net.reminitous.mineciv.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.BossEvent.BossBarColor;
import net.minecraft.world.BossEvent.BossBarOverlay;
import net.minecraft.server.level.ServerBossEvent;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.war.WarHealthSavedData;
import net.reminitous.mineciv.war.WarSavedData;
import net.reminitous.mineciv.war.WarState;

import java.util.*;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class WarBossBarEvents {

    private static final int PERIOD_TICKS = 20;

    // per-player currently shown war
    private static final Map<UUID, UUID> playerToWar = new HashMap<>();

    // warId -> bars
    private static final Map<UUID, Bars> barsByWar = new HashMap<>();

    private WarBossBarEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        MinecraftServer server = e.getServer();
        if (server.getTickCount() % PERIOD_TICKS != 0) return;

        ServerLevel overworld = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) return;

        tick(server, overworld);
    }

    private static void tick(MinecraftServer server, ServerLevel level) {
        WarSavedData warData = WarSavedData.get(server);
        CivSavedData civData = CivSavedData.get(server);
        WarHealthSavedData healthData = WarHealthSavedData.get(server);

        // 1) Ensure every online player either has bars (if in ACTIVE war) or not
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            UUID playerId = p.getUUID();

            UUID civId = civData.getPlayersCiv(playerId);
            UUID warId = (civId == null) ? null : warData.getActiveWarId(civId);

            // must be ACTIVE to show
            WarState war = (warId == null) ? null : warData.getWar(warId);
            boolean shouldShow = war != null && war.phase() == WarState.Phase.ACTIVE;

            if (!shouldShow) {
                // remove any currently shown bars
                UUID oldWar = playerToWar.remove(playerId);
                if (oldWar != null) {
                    Bars b = barsByWar.get(oldWar);
                    if (b != null) {
                        b.attacker.removePlayer(p);
                        b.defender.removePlayer(p);
                    }
                }
                continue;
            }

            // Show bars for this warId
            UUID oldWar = playerToWar.get(playerId);
            if (oldWar == null || !oldWar.equals(warId)) {
                // Switch bars
                if (oldWar != null) {
                    Bars oldBars = barsByWar.get(oldWar);
                    if (oldBars != null) {
                        oldBars.attacker.removePlayer(p);
                        oldBars.defender.removePlayer(p);
                    }
                }
                playerToWar.put(playerId, warId);

                Bars bars = barsByWar.computeIfAbsent(warId, id -> createBarsForWar(civData, war));
                bars.attacker.addPlayer(p);
                bars.defender.addPlayer(p);
            }
        }

        // 2) Update each ACTIVE war’s bar progress + titles
        Iterator<Map.Entry<UUID, Bars>> it = barsByWar.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Bars> entry = it.next();
            UUID warId = entry.getKey();
            Bars b = entry.getValue();

            WarState war = warData.getWar(warId);
            if (war == null || war.phase() != WarState.Phase.ACTIVE) {
                // cleanup bars if war not active anymore
                b.attacker.removeAllPlayers();
                b.defender.removeAllPlayers();
                it.remove();
                continue;
            }

            WarHealthSavedData.WarHealthRecord rec = healthData.get(warId);
            if (rec == null) continue;

            float aProg = progress(rec.attackerHealth, rec.attackerStartValue);
            float dProg = progress(rec.defenderHealth, rec.defenderStartValue);

            b.attacker.setProgress(aProg);
            b.defender.setProgress(dProg);

            // Titles update (names + numeric health)
            Civilization attacker = civData.getCiv(rec.attackerCivId);
            Civilization defender = civData.getCiv(rec.defenderCivId);

            String aName = safeName(attacker, rec.attackerCivId);
            String dName = safeName(defender, rec.defenderCivId);

            b.attacker.setName(Component.literal("Attacker: " + aName + "  " + Math.max(0, rec.attackerHealth) + " / " + rec.attackerStartValue));
            b.defender.setName(Component.literal("Defender: " + dName + "  " + Math.max(0, rec.defenderHealth) + " / " + rec.defenderStartValue));
        }
    }

    private static Bars createBarsForWar(CivSavedData civData, WarState war) {
        // Use placeholder names now; they’ll get updated each second anyway
        String a = String.valueOf(war.attackerCivId());
        String d = String.valueOf(war.defenderCivId());

        Civilization attacker = civData.getCiv(war.attackerCivId());
        Civilization defender = civData.getCiv(war.defenderCivId());
        if (attacker != null) a = safeName(attacker, war.attackerCivId());
        if (defender != null) d = safeName(defender, war.defenderCivId());

        ServerBossEvent attackerBar = new ServerBossEvent(
                Component.literal("Attacker: " + a),
                BossBarColor.RED,
                BossBarOverlay.PROGRESS
        );
        attackerBar.setVisible(true);

        ServerBossEvent defenderBar = new ServerBossEvent(
                Component.literal("Defender: " + d),
                BossBarColor.BLUE,
                BossBarOverlay.PROGRESS
        );
        defenderBar.setVisible(true);

        return new Bars(attackerBar, defenderBar);
    }

    private static float progress(long health, long start) {
        if (start <= 0) return 0f;
        double v = (double) health / (double) start;
        if (v < 0) v = 0;
        if (v > 1) v = 1;
        return (float) v;
    }

    private static String safeName(Civilization civ, UUID id) {
        if (civ == null) return (id == null ? "Unknown" : id.toString());
        String n = civ.name();
        if (n == null || n.isBlank()) return (id == null ? "Unknown" : id.toString());
        return n;
    }

    private record Bars(ServerBossEvent attacker, ServerBossEvent defender) {}
}
