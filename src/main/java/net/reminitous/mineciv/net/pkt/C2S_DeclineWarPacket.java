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

public final class C2S_DeclineWarPacket {

    private final UUID warId;

    public C2S_DeclineWarPacket(UUID warId) {
        this.warId = warId;
    }

    public static void encode(C2S_DeclineWarPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.warId);
    }

    public static C2S_DeclineWarPacket decode(FriendlyByteBuf buf) {
        return new C2S_DeclineWarPacket(buf.readUUID());
    }

    public static void handle(C2S_DeclineWarPacket msg, CustomPayloadEvent.Context ctx) {
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
                player.sendSystemMessage(Component.literal("Only the leader may decline war proposals."));
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

            // Decline policy: war starts anyway 24h after decline.
            // We'll store the "force start time" using preparationEndsAtMs (already used by your tick).
            long forceStart = now + 24L * 60L * 60L * 1000L;

            war.setDefenderAccepted(false);
            war.setPreparationEndsAtMs(forceStart);

            // Keep it in PROPOSED so your tick logic starts it when now >= preparationEndsAtMs
            war.setPhase(WarState.Phase.PROPOSED);

            warData.putWar(war);

            player.sendSystemMessage(Component.literal("War declined. It will begin in 24 hours unless resolved otherwise."));
        });

        ctx.setPacketHandled(true);
    }
}
