package net.reminitous.mineciv.net.pkt;

import net.minecraft.network.FriendlyByteBuf;

import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.client.screen.WarProposalScreen;
import net.reminitous.mineciv.client.ClientScreens;

import java.util.UUID;

public final class S2C_OpenWarProposalScreenPacket {

    public final UUID warId;
    public final UUID attackerCivId;
    public final String attackerName;
    public final int preparationMinutes;
    public final long proposedAtMs;

    public S2C_OpenWarProposalScreenPacket(UUID warId,
                                           UUID attackerCivId,
                                           String attackerName,
                                           int preparationMinutes,
                                           long proposedAtMs) {
        this.warId = warId;
        this.attackerCivId = attackerCivId;
        this.attackerName = attackerName;
        this.preparationMinutes = preparationMinutes;
        this.proposedAtMs = proposedAtMs;
    }

    public static void encode(S2C_OpenWarProposalScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.warId);
        buf.writeUUID(msg.attackerCivId);
        buf.writeUtf(msg.attackerName == null ? "" : msg.attackerName, 64);
        buf.writeVarInt(msg.preparationMinutes);
        buf.writeLong(msg.proposedAtMs);
    }

    public static S2C_OpenWarProposalScreenPacket decode(FriendlyByteBuf buf) {
        UUID warId = buf.readUUID();
        UUID attacker = buf.readUUID();
        String name = buf.readUtf(64);
        int prep = buf.readVarInt();
        long proposed = buf.readLong();
        return new S2C_OpenWarProposalScreenPacket(warId, attacker, name, prep, proposed);
    }

    public static void handle(S2C_OpenWarProposalScreenPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> ClientScreens.open(new WarProposalScreen(msg)));
        ctx.setPacketHandled(true);
    }
}
