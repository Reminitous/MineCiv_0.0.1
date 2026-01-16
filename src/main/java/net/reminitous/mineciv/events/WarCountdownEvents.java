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
import net.reminitous.mineciv.war.WarSavedData;
import net.reminitous.mineciv.war.WarState;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class WarCountdownEvents {

    private WarCountdownEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        ServerLevel level = server.getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (level == null) return;

        WarSavedData warData = WarSavedData.get(server);
        CivSavedData civData = CivSavedData.get(server);

        long now = System.currentTimeMillis();

        for (WarState war : warData.wars().values()) {
            if (war == null) continue;

            // Countdown is only relevant if war is about to become ACTIVE
            if (war.phase() != WarState.Phase.PREPARING) continue;

            long startAt = war.preparationEndsAtMs();
            if (startAt <= 0) continue;

            long remainingMs = startAt - now;
            if (remainingMs <= 0) continue;

            long remainingSec = remainingMs / 1000L;

            // Only announce within last 60 seconds
            if (remainingSec > 60) continue;

            int sec = (int) remainingSec;

            // announce at 60, 30, 10..1
            boolean shouldAnnounce =
                    sec == 60 || sec == 30 || (sec <= 10 && sec >= 1);

            if (!shouldAnnounce) continue;

            // Deduplicate per-second announcements using a bitmask
            // bits 0..60 represent seconds 0..60
            int mask = war.leaderWarnMask();
            int bit = 1 << Math.min(30, sec); // protect shift overflow for weird values
            if (sec <= 30) bit = 1 << sec;

            if ((mask & bit) != 0) continue;

            war.setLeaderWarnMask(mask | bit);
            warData.putWar(war);

            Civilization attacker = civData.getCiv(war.attackerCivId());
            Civilization defender = civData.getCiv(war.defenderCivId());

            String aName = safeName(attacker, war.attackerCivId());
            String dName = safeName(defender, war.defenderCivId());

            Component msg = Component.literal("⚔ War begins in " + sec + "s: " + aName + " vs " + dName);

            broadcastToCiv(level, attacker, msg);
            broadcastToCiv(level, defender, msg);
        }
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
