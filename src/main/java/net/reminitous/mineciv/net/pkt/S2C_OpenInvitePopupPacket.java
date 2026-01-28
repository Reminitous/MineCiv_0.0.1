package net.reminitous.mineciv.net.pkt;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.client.screen.InvitePopupScreen;
import net.reminitous.mineciv.civ.CivClass;

import java.util.UUID;

public final class S2C_OpenInvitePopupPacket {

    public final UUID civId;
    public final String civName;
    public final CivClass classType;

    public S2C_OpenInvitePopupPacket(UUID civId, String civName, CivClass classType) {
        this.civId = civId;
        this.civName = civName;
        this.classType = classType;
    }

    public static void encode(S2C_OpenInvitePopupPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.civId);
        buf.writeUtf(msg.civName, 32);
        buf.writeEnum(msg.classType);
    }

    public static S2C_OpenInvitePopupPacket decode(FriendlyByteBuf buf) {
        UUID civId = buf.readUUID();
        String name = buf.readUtf(32);
        CivClass type = buf.readEnum(CivClass.class);
        return new S2C_OpenInvitePopupPacket(civId, name, type);
    }

    public static void handle(S2C_OpenInvitePopupPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.setScreen(new InvitePopupScreen(msg));
        });
        ctx.setPacketHandled(true);
    }
}
