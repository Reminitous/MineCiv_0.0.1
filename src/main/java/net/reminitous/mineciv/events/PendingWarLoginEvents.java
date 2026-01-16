package net.reminitous.mineciv.events;

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
public final class PendingWarLoginEvents {

    private PendingWarLoginEvents() {}

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        var server = level.getServer();

        CivSavedData civData = CivSavedData.get(server);
        UUID civId = civData.getPlayersCiv(player.getUUID());
        if (civId == null) return;

        Civilization civ = civData.getCiv(civId);
        if (civ == null) return;

        // Only the civ leader gets the popup
        if (civ.leader() == null || !civ.leader().equals(player.getUUID())) return;

        WarSavedData warData = WarSavedData.get(server);

        // Only ONE pending proposal allowed per civ (your rule)
        UUID pendingWarId = warData.getPendingWarId(civId);
        if (pendingWarId == null) return;

        WarState war = warData.getWar(pendingWarId);
        if (war == null) return;

        // Only show popup for PROPOSED wars
        if (war.phase() != WarState.Phase.PROPOSED) return;

        // Only the DEFENDER leader should get accept/decline popup
        if (war.defenderCivId() == null || !war.defenderCivId().equals(civId)) return;

        UUID attackerId = war.attackerCivId();
        Civilization attacker = attackerId == null ? null : civData.getCiv(attackerId);
        String attackerName = (attacker != null && attacker.name() != null && !attacker.name().isBlank())
                ? attacker.name()
                : String.valueOf(attackerId);

        Network.CH.send(
                new S2C_OpenWarProposalScreenPacket(
                        war.warId(),
                        attackerId,
                        attackerName,
                        war.preparationMinutes(),
                        war.proposedAtMs()
                ),
                PacketDistributor.PLAYER.with(player)
        );
    }
}
