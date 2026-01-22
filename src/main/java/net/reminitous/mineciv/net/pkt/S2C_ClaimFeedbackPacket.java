package net.reminitous.mineciv.net.pkt;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.client.screen.ManageCivScreen;

public final class S2C_ClaimFeedbackPacket {

    public final String text;
    public final int color;
    public final int ttlTicks;

    public S2C_ClaimFeedbackPacket(String text, int color, int ttlTicks) {
        this.text = text == null ? "" : text;
        this.color = color;
        this.ttlTicks = ttlTicks;
    }

    public static void encode(S2C_ClaimFeedbackPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.text, 32767);
        buf.writeInt(msg.color);
        buf.writeVarInt(msg.ttlTicks);
    }

    public static S2C_ClaimFeedbackPacket decode(FriendlyByteBuf buf) {
        String text = buf.readUtf(32767);
        int color = buf.readInt();
        int ttl = buf.readVarInt();
        return new S2C_ClaimFeedbackPacket(text, color, ttl);
    }

    public static void handle(S2C_ClaimFeedbackPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen instanceof ManageCivScreen screen) {
                screen.mineciv_setClaimFeedback(msg.text, msg.color, msg.ttlTicks);
            } else if (mc.player != null) {
                // fallback if screen not open
                mc.player.sendSystemMessage(net.minecraft.network.chat.Component.literal(msg.text));
            }
        });
        ctx.setPacketHandled(true);
    }
}
