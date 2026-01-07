package net.reminitous.mineciv.net.pkt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.client.Minecraft;

import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.client.screen.WarProposalScreen;

import java.util.UUID;

public final class S2C_OpenWarProposalScreenPacket {

    public final UUID warId;
    public final UUID attackerCivId;
    public final String attackerCivName;
    public final int prepMinutes;
    public final long proposedAtMs;

    public S2C_OpenWarProposalScreenPacket(UUID warId, UUID attackerCivId, String attackerCivName, int prepMinutes, long proposedAtMs) {
        this.warId = warId;
        this.attackerCivId = attackerCivId;
        this.attackerCivName = attackerCivName;
        this.prepMinutes = prepMinutes;
        this.proposedAtMs = proposedAtMs;
    }

    public static void encode(S2C_OpenWarProposalScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.warId);
        buf.writeUUID(msg.attackerCivId);
        buf.writeUtf(msg.attackerCivName == null ? "" : msg.attackerCivName, 64);
        buf.writeVarInt(msg.prepMinutes);
        buf.writeLong(msg.proposedAtMs);
    }

    public static S2C_OpenWarProposalScreenPacket decode(FriendlyByteBuf buf) {
        UUID warId = buf.readUUID();
        UUID attackerId = buf.readUUID();
        String attackerName = buf.readUtf(64);
        int prep = buf.readVarInt();
        long proposedAt = buf.readLong();
        return new S2C_OpenWarProposalScreenPacket(warId, attackerId, attackerName, prep, proposedAt);
    }

    public static void handle(S2C_OpenWarProposalScreenPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.setScreen(new WarProposalScreen(msg));
        });
        ctx.setPacketHandled(true);
    }
}
