package net.reminitous.mineciv.net.pkt;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import net.minecraftforge.event.network.CustomPayloadEvent;

public final class S2C_ForceCloseMineCivUiPacket {

    public S2C_ForceCloseMineCivUiPacket() {}

    public static void encode(S2C_ForceCloseMineCivUiPacket msg, FriendlyByteBuf buf) {
        // no data
    }

    public static S2C_ForceCloseMineCivUiPacket decode(FriendlyByteBuf buf) {
        return new S2C_ForceCloseMineCivUiPacket();
    }

    public static void handle(S2C_ForceCloseMineCivUiPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) {
                mc.setScreen(null);
            }
        });
        ctx.setPacketHandled(true);
    }
}
