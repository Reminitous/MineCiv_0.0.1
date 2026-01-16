package net.reminitous.mineciv.net.pkt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
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

            var server = level.getServer();
            WarSavedData warData = WarSavedData.get(server);
            WarState war = warData.getWar(msg.warId);

            if (war == null) {
                player.sendSystemMessage(Component.literal("War not found."));
                return;
            }
            if (war.phase() != WarState.Phase.PROPOSED) {
                player.sendSystemMessage(Component.literal("This war is no longer awaiting a response."));
                return;
            }

            UUID attackerCivId = war.attackerCivId();
            UUID defenderCivId = war.defenderCivId();
            if (attackerCivId == null || defenderCivId == null) {
                player.sendSystemMessage(Component.literal("War data is invalid."));
                return;
            }

            // "Only one proposal at a time" enforcement:
            // You can only decline the war that is currently pending for your civ.
            UUID defenderPending = warData.getPendingWarId(defenderCivId);
            UUID attackerPending = warData.getPendingWarId(attackerCivId);
            if (defenderPending == null || !defenderPending.equals(war.warId())) {
                player.sendSystemMessage(Component.literal("This war is not the currently pending war for your civilization."));
                return;
            }
            if (attackerPending == null || !attackerPending.equals(war.warId())) {
                player.sendSystemMessage(Component.literal("Attacker no longer has this war pending."));
                return;
            }

            // Defender leader check
            CivSavedData civData = CivSavedData.get(server);
            Civilization defender = civData.getCiv(defenderCivId);
            if (defender == null || defender.leader() == null || !defender.leader().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Only the defending civilization leader may decline."));
                return;
            }

            long now = System.currentTimeMillis();

            // DECLINE:
            // war remains PROPOSED, but force-start becomes 24h from now (or earlier if already sooner).
            war.setDefenderAccepted(false);

            long startAt24h = now + 24L * 60L * 60L * 1000L;

            long existingForce = war.preparationEndsAtMs(); // used as "force start" during PROPOSED in this v1
            if (existingForce <= 0L) {
                war.setPreparationEndsAtMs(startAt24h);
            } else {
                war.setPreparationEndsAtMs(Math.min(existingForce, startAt24h));
            }

            // Once declined, leader countdown is irrelevant
            war.setLeaderOnlineDeadlineMs(0L);
            war.setLeaderWarnMask(0);

            warData.putWar(war);

            // Keep pending mapping (still a pending war; it may auto-start later)
            warData.setPendingWar(attackerCivId, war.warId());
            warData.setPendingWar(defenderCivId, war.warId());

            player.sendSystemMessage(Component.literal("War declined. War will begin automatically within 24 hours."));
        });

        ctx.setPacketHandled(true);
    }
}
