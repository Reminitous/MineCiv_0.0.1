package net.reminitous.mineciv.net.pkt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import net.minecraft.client.Minecraft;

public final class S2C_CloseScreenPacket {

    public S2C_CloseScreenPacket() {}

    public static void encode(S2C_CloseScreenPacket msg, FriendlyByteBuf buf) {
        // no payload
    }

    public static S2C_CloseScreenPacket decode(FriendlyByteBuf buf) {
        return new S2C_CloseScreenPacket();
    }

    public static void handle(S2C_CloseScreenPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                mc.setScreen(null); // closes current screen
            }
        });
        ctx.setPacketHandled(true);
    }
}
