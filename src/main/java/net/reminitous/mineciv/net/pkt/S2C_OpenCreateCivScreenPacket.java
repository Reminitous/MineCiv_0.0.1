package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.fml.DistExecutor;

public final class S2C_OpenCreateCivScreenPacket {

    private final BlockPos monumentPos;

    public S2C_OpenCreateCivScreenPacket(BlockPos monumentPos) {
        this.monumentPos = monumentPos;
    }

    public static void encode(S2C_OpenCreateCivScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
    }

    public static S2C_OpenCreateCivScreenPacket decode(FriendlyByteBuf buf) {
        return new S2C_OpenCreateCivScreenPacket(buf.readBlockPos());
    }

    public static void handle(S2C_OpenCreateCivScreenPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            // TODO: replace with your actual screen class
            // net.minecraft.client.Minecraft.getInstance().setScreen(new CreateCivScreen(msg.monumentPos));
        }));

        ctx.setPacketHandled(true);
    }
}
