package net.reminitous.mineciv.net.pkt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivSavedData;
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

            // Player must be in a civ and be leader
            var civOpt = CivilizationManager.findPlayerCiv(level, player.getUUID());
            if (civOpt.isEmpty()) {
                player.sendSystemMessage(Component.literal("You are not in a civilization."));
                return;
            }
            Civilization civ = civOpt.get();

            if (civ.leader() == null || !civ.leader().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Only civilization leaders may respond to war proposals."));
                return;
            }

            WarSavedData warData = WarSavedData.get(level.getServer());
            WarState war = warData.getWar(msg.warId);
            if (war == null) {
                player.sendSystemMessage(Component.literal("War proposal not found."));
                return;
            }

            // Must be the defender leader
            if (war.defenderCivId() == null || !war.defenderCivId().equals(civ.id())) {
                player.sendSystemMessage(Component.literal("You are not the defender for this war proposal."));
                return;
            }

            if (war.phase() != WarState.Phase.PROPOSED) {
                player.sendSystemMessage(Component.literal("This war proposal is no longer pending."));
                return;
            }

            long now = System.currentTimeMillis();

            war.setDefenderAccepted(true);
            war.setPhase(WarState.Phase.PREPARING);
            war.setPreparationEndsAtMs(now + (long) war.preparationMinutes() * 60L * 1000L);

            warData.putWar(war);

            player.sendSystemMessage(Component.literal("War accepted. Preparation started (" + war.preparationMinutes() + " minutes)."));
        });

        ctx.setPacketHandled(true);
    }
}
