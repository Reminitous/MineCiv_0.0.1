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
import net.reminitous.mineciv.war.WarStatusSavedData;

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
            // You can only accept the war that is currently pending for your civ.
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
                player.sendSystemMessage(Component.literal("Only the defending civilization leader may accept."));
                return;
            }

            // Cooldown enforcement (on accept)
            WarStatusSavedData status = WarStatusSavedData.get(server);
            long now = System.currentTimeMillis();

            if (status.isInGrace(defenderCivId, now)) {
                player.sendSystemMessage(Component.literal("Your civilization is in a grace period and cannot enter a new war."));
                return;
            }
            if (status.isInGrace(attackerCivId, now)) {
                player.sendSystemMessage(Component.literal("The attacker civilization is in a grace period and cannot enter a new war."));
                return;
            }
            if (status.isRematchBlocked(attackerCivId, defenderCivId, now)) {
                player.sendSystemMessage(Component.literal("Rematch cooldown active: attacker cannot war your civ yet."));
                return;
            }

            // Also enforce: active war mapping must be empty for both civs (ACTIVE only)
            UUID aActive = warData.getActiveWarId(attackerCivId);
            UUID dActive = warData.getActiveWarId(defenderCivId);
            if (aActive != null) {
                player.sendSystemMessage(Component.literal("Attacker is already in an active war."));
                return;
            }
            if (dActive != null) {
                player.sendSystemMessage(Component.literal("Your civilization is already in an active war."));
                return;
            }

            // ACCEPT -> PREPARING
            war.setDefenderAccepted(true);
            war.setPhase(WarState.Phase.PREPARING);

            long prepMs = (long) war.preparationMinutes() * 60L * 1000L;
            war.setPreparationEndsAtMs(now + prepMs);

            // Once accepted, leader-online countdown no longer matters
            war.setLeaderOnlineDeadlineMs(0L);
            war.setLeaderWarnMask(0);

            warData.putWar(war);

            // Ensure pending mapping remains correct (proposal already set it, but keep safe)
            warData.setPendingWar(attackerCivId, war.warId());
            warData.setPendingWar(defenderCivId, war.warId());

            player.sendSystemMessage(Component.literal("War accepted. Preparation has begun (" + war.preparationMinutes() + "m)."));
        });

        ctx.setPacketHandled(true);
    }
}
