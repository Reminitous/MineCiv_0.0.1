package net.reminitous.mineciv.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

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

    private static final int PERIOD_TICKS = 20; // once per second

    private WarTickEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        var server = e.getServer();
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

            // Skip ended wars
            if (war.phase() == WarState.Phase.ENDED) continue;

            UUID aId = war.attackerCivId();
            UUID dId = war.defenderCivId();
            if (aId == null || dId == null) continue;

            Civilization attacker = civData.getCiv(aId);
            Civilization defender = civData.getCiv(dId);
            if (attacker == null || defender == null) continue;

            // --- Phase transitions ---
            if (war.phase() == WarState.Phase.PREPARING) {
                if (now >= war.preparationEndsAtMs()) {
                    startActive(level, warData, war, attacker, defender);
                }
                continue;
            }

            if (war.phase() == WarState.Phase.PROPOSED) {
                long forceAt = war.preparationEndsAtMs(); // used as force-start timestamp (decline/no-response policy)
                if (forceAt > 0 && now >= forceAt) {
                    startActive(level, warData, war, attacker, defender);
                }
                continue;
            }

            // --- ACTIVE war: keep checking for end condition ---
            if (war.phase() == WarState.Phase.ACTIVE) {
                // Ensure health exists (in case server restarted mid-war)
                WarHealthManager.initializeIfMissing(level, war);

                // Try end if someone is at/below zero health
                WarEndManager.tryEndIfDefeated(level, war);
            }
        }
    }

    private static void startActive(ServerLevel level,
                                    WarSavedData warData,
                                    WarState war,
                                    Civilization attacker,
                                    Civilization defender) {

        war.setPhase(WarState.Phase.ACTIVE);
        warData.putWar(war);

        // Active mapping should only be set when ACTIVE
        warData.setActiveWar(attacker.id(), war.warId());
        warData.setActiveWar(defender.id(), war.warId());

        // Initialize health snapshot once
        WarHealthManager.initializeIfMissing(level, war);

        // Notify both civs
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
