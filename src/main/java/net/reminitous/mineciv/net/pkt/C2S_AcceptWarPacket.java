package net.reminitous.mineciv.net.pkt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.war.WarSavedData;
import net.reminitous.mineciv.war.WarState;

import java.util.UUID;

public final class C2S_AcceptWarPacket {

    private final UUID warId;

    public C2S_AcceptWarPacket(UUID warId) {
        this.warId = warId;
    }

    public static void encode(C2S_AcceptWarPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.warId);
    }

    public static C2S_AcceptWarPacket decode(FriendlyByteBuf buf) {
        return new C2S_AcceptWarPacket(buf.readUUID());
    }

    public static void handle(C2S_AcceptWarPacket msg, CustomPayloadEvent.Context ctx) {
        Object s = ctx.getSender();
        if (!(s instanceof ServerPlayer player)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) return;

            var civOpt = CivilizationManager.findPlayerCiv(level, player.getUUID());
            if (civOpt.isEmpty()) {
                player.sendSystemMessage(Component.literal("You are not in a civilization."));
                return;
            }
            Civilization civ = civOpt.get();

            if (civ.leader() == null || !civ.leader().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Only the leader may accept war proposals."));
                return;
            }

            WarSavedData warData = WarSavedData.get(level.getServer());
            WarState war = warData.getWar(msg.warId);
            if (war == null) {
                player.sendSystemMessage(Component.literal("That war proposal no longer exists."));
                return;
            }

            if (war.phase() != WarState.Phase.PROPOSED) {
                player.sendSystemMessage(Component.literal("That war proposal is no longer pending."));
                return;
            }

            if (war.defenderCivId() == null || !war.defenderCivId().equals(civ.id())) {
                player.sendSystemMessage(Component.literal("You are not the defending civilization for this proposal."));
                return;
            }

            long now = System.currentTimeMillis();
            war.setPhase(WarState.Phase.PREPARING);
            war.setPreparationEndsAtMs(now + war.preparationMinutes() * 60L * 1000L);
            war.setDefenderAccepted(true);

            warData.putWar(war);

            player.sendSystemMessage(Component.literal("War accepted. Preparation time: " + war.preparationMinutes() + " minutes."));
        });

        ctx.setPacketHandled(true);
    }
}
