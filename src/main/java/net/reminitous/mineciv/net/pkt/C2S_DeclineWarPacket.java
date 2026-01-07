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

    private static final long DECLINE_DELAY_MS = 24L * 60L * 60L * 1000L; // 24 hours

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
                player.sendSystemMessage(Component.literal("Only the defender leader can decline this war."));
                return;
            }

            long now = System.currentTimeMillis();
            war.setDefenderAccepted(false);
            war.setPhase(WarState.Phase.PREPARING);
            war.setPreparationEndsAtMs(now + DECLINE_DELAY_MS);

            warData.putWar(war);

            // Notify both civs
            Civilization attacker = civData.getCiv(war.attackerCivId());
            String defenderName = defender.name() == null ? defenderCivId.toString() : defender.name();

            notifyCiv(level, defender, Component.literal("⚔ War declined. War will begin in 24 hours."));
            if (attacker != null) {
                notifyCiv(level, attacker, Component.literal("⚔ " + defenderName + " declined. War will begin in 24 hours."));
            }

            player.sendSystemMessage(Component.literal("Declined. War will begin in 24 hours."));
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
