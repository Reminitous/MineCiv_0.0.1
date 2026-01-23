package net.reminitous.mineciv.net.pkt;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import net.minecraftforge.event.network.CustomPayloadEvent;

public final class S2C_ClaimFeedbackPacket {

    public final boolean success;
    public final String message;

    // Optional UI helpers
    public final int newClaimCredits;     // -1 if unknown
    public final boolean hasChunk;
    public final int chunkX;
    public final int chunkZ;

    public S2C_ClaimFeedbackPacket(boolean success, String message, int newClaimCredits, boolean hasChunk, int chunkX, int chunkZ) {
        this.success = success;
        this.message = message == null ? "" : message;
        this.newClaimCredits = newClaimCredits;
        this.hasChunk = hasChunk;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
    }

    public static void encode(S2C_ClaimFeedbackPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.success);
        buf.writeUtf(msg.message);
        buf.writeVarInt(msg.newClaimCredits);

        buf.writeBoolean(msg.hasChunk);
        if (msg.hasChunk) {
            buf.writeVarInt(msg.chunkX);
            buf.writeVarInt(msg.chunkZ);
        }
    }

    public static S2C_ClaimFeedbackPacket decode(FriendlyByteBuf buf) {
        boolean success = buf.readBoolean();
        String message = buf.readUtf();
        int credits = buf.readVarInt();

        boolean hasChunk = buf.readBoolean();
        int x = 0, z = 0;
        if (hasChunk) {
            x = buf.readVarInt();
            z = buf.readVarInt();
        }

        return new S2C_ClaimFeedbackPacket(success, message, credits, hasChunk, x, z);
    }

    public static void handle(S2C_ClaimFeedbackPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                // Chat feedback
                mc.player.sendSystemMessage(Component.literal(msg.message));
            }

            // If the ManageCivScreen is open, refresh it (so credits/chunks update instantly)
            if (mc.screen instanceof net.reminitous.mineciv.client.screen.ManageCivScreen screen) {
                screen.requestRefreshFromClient();
            }
        });

        ctx.setPacketHandled(true);
    }
}
