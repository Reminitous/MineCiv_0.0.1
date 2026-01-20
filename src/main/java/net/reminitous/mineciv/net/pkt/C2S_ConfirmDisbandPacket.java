package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivilizationManager;

import java.util.UUID;

public final class C2S_ConfirmDisbandPacket {

    private final BlockPos monumentPos;
    private final UUID civId;

    public C2S_ConfirmDisbandPacket(BlockPos monumentPos, UUID civId) {
        this.monumentPos = monumentPos;
        this.civId = civId;
    }

    public static void encode(C2S_ConfirmDisbandPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
    }

    public static C2S_ConfirmDisbandPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        return new C2S_ConfirmDisbandPacket(pos, civId);
    }

    public static void handle(C2S_ConfirmDisbandPacket msg, CustomPayloadEvent.Context ctx) {
        Object s = ctx.getSender();
        if (!(s instanceof ServerPlayer player)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) return;

            boolean ok = CivilizationManager.disbandCiv(level, msg.civId, player.getUUID(), msg.monumentPos);

            if (ok) {
                player.sendSystemMessage(Component.literal("Civilization disbanded."));
            } else {
                player.sendSystemMessage(Component.literal("Failed to disband (must be leader at your monument)."));
            }
        });

        ctx.setPacketHandled(true);
    }
}
