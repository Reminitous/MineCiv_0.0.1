package net.reminitous.mineciv.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.S2C_OpenWarProposalScreenPacket;
import net.reminitous.mineciv.war.WarSavedData;
import net.reminitous.mineciv.war.WarState;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class WarProposalLoginEvents {

    private WarProposalLoginEvents() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        CivSavedData civData = CivSavedData.get(level.getServer());
        UUID civId = civData.getPlayersCiv(player.getUUID());
        if (civId == null) return;

        Civilization civ = civData.getCiv(civId);
        if (civ == null) return;

        if (civ.leader() == null || !civ.leader().equals(player.getUUID())) return;

        WarSavedData warData = WarSavedData.get(level.getServer());

        WarState proposed = null;
        for (WarState w : warData.wars().values()) {
            if (w == null) continue;
            if (w.phase() != WarState.Phase.PROPOSED) continue;
            if (w.defenderCivId() == null || !w.defenderCivId().equals(civId)) continue;
            proposed = w;
            break;
        }
        if (proposed == null) return;

        long now = System.currentTimeMillis();

        // Start the 1-hour deadline once, and send the 60m warning right then.
        if (proposed.leaderOnlineDeadlineMs() <= 0L) {
            proposed.setLeaderOnlineDeadlineMs(now + 60L * 60L * 1000L);
            proposed.setLeaderWarnMask(0); // clear warnings
            warData.putWar(proposed);

            player.sendSystemMessage(Component.literal(
                    "⚠ War proposal pending. You have 60 minutes to Accept/Decline or war will begin automatically."
            ));
        }

        // Build attacker name for UI
        UUID attackerId = proposed.attackerCivId();
        String attackerName = String.valueOf(attackerId);
        if (attackerId != null) {
            Civilization attacker = civData.getCiv(attackerId);
            if (attacker != null && attacker.name() != null && !attacker.name().isBlank()) {
                attackerName = attacker.name();
            }
        }

        S2C_OpenWarProposalScreenPacket pkt = new S2C_OpenWarProposalScreenPacket(
                proposed.warId(),
                attackerId,
                attackerName,
                proposed.preparationMinutes(),
                proposed.proposedAtMs()
        );

        Network.CH.send(pkt, PacketDistributor.PLAYER.with(player));
    }
}
