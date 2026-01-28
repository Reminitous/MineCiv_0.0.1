package net.reminitous.mineciv.events;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivClass;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.S2C_OpenInvitePopupPacket;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class InviteLoginEvents {

    private InviteLoginEvents() {}

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        CivSavedData data = CivSavedData.get(player.getServer());
        UUID civId = data.getPendingInvite(player.getUUID());
        if (civId == null) return;

        Civilization civ = data.getCiv(civId);
        if (civ == null) {
            data.setPendingInvite(player.getUUID(), null);
            return;
        }

        Network.CH.send(
                new S2C_OpenInvitePopupPacket(
                        civ.id(),
                        civ.name() == null ? "" : civ.name(),
                        civ.classType() == null ? CivClass.AGRICULTURAL : civ.classType()
                ),
                PacketDistributor.PLAYER.with(player)
        );
    }
}
