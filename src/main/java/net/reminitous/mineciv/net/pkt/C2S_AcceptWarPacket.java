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

            WarSavedData warData = WarSavedData.get(level.getServer());
            WarState war = warData.getWar(msg.warId);
            if (war == null) return;

            if (war.phase() != WarState.Phase.PROPOSED) {
                player.sendSystemMessage(Component.literal("This war proposal is no longer pending."));
                return;
            }

            UUID defenderCivId = war.defenderCivId();
            if (defenderCivId == null) return;

            CivSavedData civData = CivSavedData.get(level.getServer());
            Civilization defender = civData.getCiv(defenderCivId);
            if (defender == null) return;

            // Must be defender leader
            if (defender.leader() == null || !defender.leader().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Only the defender leader can accept this war."));
                return;
            }

            long now = System.currentTimeMillis();
            long prepEnds = now + (long) war.preparationMinutes() * 60_000L;

            war.setDefenderAccepted(true);
            war.setPhase(WarState.Phase.PREPARING);
            war.setPreparationEndsAtMs(prepEnds);

            warData.putWar(war);

            // Notify both civs
            Civilization attacker = civData.getCiv(war.attackerCivId());
            String attackerName = attacker == null ? war.attackerCivId().toString() : (attacker.name() == null ? war.attackerCivId().toString() : attacker.name());
            String defenderName = defender.name() == null ? defenderCivId.toString() : defender.name();

            notifyCiv(level, defender, Component.literal("⚔ War accepted! Preparation: " + war.preparationMinutes() + " minutes."));
            if (attacker != null) {
                notifyCiv(level, attacker, Component.literal("⚔ Your war proposal was accepted by " + defenderName + "! Prep: " + war.preparationMinutes() + " minutes."));
            }

            player.sendSystemMessage(Component.literal("War accepted. It will begin after preparation."));
        });

        ctx.setPacketHandled(true);
    }

    private static void notifyCiv(ServerLevel level, Civilization civ, Component msg) {
        for (UUID memberId : civ.members()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) p.sendSystemMessage(msg);
        }
    }
}
