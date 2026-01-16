package net.reminitous.mineciv.net.pkt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.war.WarSavedData;
import net.reminitous.mineciv.war.WarState;

import java.util.UUID;

public final class C2S_RequestOpenWarProposalPacket {

    private final UUID warId;

    public C2S_RequestOpenWarProposalPacket(UUID warId) {
        this.warId = warId;
    }

    public static void encode(C2S_RequestOpenWarProposalPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.warId);
    }

    public static C2S_RequestOpenWarProposalPacket decode(FriendlyByteBuf buf) {
        return new C2S_RequestOpenWarProposalPacket(buf.readUUID());
    }

    public static void handle(C2S_RequestOpenWarProposalPacket msg, CustomPayloadEvent.Context ctx) {
        Object s = ctx.getSender();
        if (!(s instanceof ServerPlayer player)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) return;

            var server = level.getServer();
            WarSavedData warData = WarSavedData.get(server);
            WarState war = warData.getWar(msg.warId);
            if (war == null) {
                player.sendSystemMessage(Component.literal("War not found."));
                return;
            }

            // Must be defender leader, and war must still be PROPOSED
            if (war.phase() != WarState.Phase.PROPOSED) {
                player.sendSystemMessage(Component.literal("This war is no longer awaiting a response."));
                return;
            }

            UUID defenderCivId = war.defenderCivId();
            if (defenderCivId == null) return;

            CivSavedData civData = CivSavedData.get(server);
            Civilization defender = civData.getCiv(defenderCivId);
            if (defender == null || defender.leader() == null || !defender.leader().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Only the defending leader can open this proposal."));
                return;
            }

            UUID attackerCivId = war.attackerCivId();
            String attackerName = String.valueOf(attackerCivId);
            if (attackerCivId != null) {
                Civilization attacker = civData.getCiv(attackerCivId);
                if (attacker != null && attacker.name() != null && !attacker.name().isBlank()) {
                    attackerName = attacker.name();
                }
            }

            Network.CH.send(
                    new S2C_OpenWarProposalScreenPacket(
                            war.warId(),
                            attackerCivId,
                            attackerName,
                            war.preparationMinutes(),
                            war.proposedAtMs()
                    ),
                    PacketDistributor.PLAYER.with(player)
            );
        });

        ctx.setPacketHandled(true);
    }
}
