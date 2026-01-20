package net.reminitous.mineciv.net.pkt;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.client.screen.DisbandConfirmScreen;

import java.util.UUID;

public final class S2C_OpenDisbandConfirmScreenPacket {

    public final BlockPos monumentPos;
    public final UUID civId;
    public final String civName;

    public S2C_OpenDisbandConfirmScreenPacket(BlockPos monumentPos, UUID civId, String civName) {
        this.monumentPos = monumentPos;
        this.civId = civId;
        this.civName = civName;
    }

    public static void encode(S2C_OpenDisbandConfirmScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
        buf.writeUtf(msg.civName == null ? "" : msg.civName, 64);
    }

    public static S2C_OpenDisbandConfirmScreenPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        String name = buf.readUtf(64);
        return new S2C_OpenDisbandConfirmScreenPacket(pos, civId, name);
    }

    public static void handle(S2C_OpenDisbandConfirmScreenPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.setScreen(new DisbandConfirmScreen(msg.monumentPos, msg.civId, msg.civName));
        });
        ctx.setPacketHandled(true);
    }
}
