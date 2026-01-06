package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.UUID;

public final class C2S_CancelInvitePacket {

    private final BlockPos monumentPos;
    private final UUID civId;
    private final UUID invitedPlayerId;

    public C2S_CancelInvitePacket(BlockPos monumentPos, UUID civId, UUID invitedPlayerId) {
        this.monumentPos = monumentPos;
        this.civId = civId;
        this.invitedPlayerId = invitedPlayerId;
    }

    public static void encode(C2S_CancelInvitePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
        buf.writeUUID(msg.invitedPlayerId);
    }

    public static C2S_CancelInvitePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        UUID invited = buf.readUUID();
        return new C2S_CancelInvitePacket(pos, civId, invited);
    }

    public static void handle(C2S_CancelInvitePacket msg, CustomPayloadEvent.Context ctx) {
        var sender = ctx.getSender();
        if (!(sender instanceof ServerPlayer leader)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(leader.level() instanceof ServerLevel level)) return;
            if (!level.hasChunkAt(msg.monumentPos)) return;

            var be = level.getBlockEntity(msg.monumentPos);
            if (!(be instanceof net.reminitous.mineciv.monument.MonumentBlockEntity monumentBE)) return;

            if (!monumentBE.isBound()) return;
            if (monumentBE.getCivId() == null) return;
            if (!monumentBE.getCivId().equals(msg.civId)) return;

            CivSavedData data = CivSavedData.get(level.getServer());
            Civilization civ = data.getCiv(msg.civId);
            if (civ == null) return;

            // leader-only
            if (civ.leader() == null || !civ.leader().equals(leader.getUUID())) return;

            UUID pending = data.getPendingInvite(msg.invitedPlayerId);
            if (pending == null || !pending.equals(msg.civId)) return;

            data.setPendingInvite(msg.invitedPlayerId, null);

            leader.sendSystemMessage(net.minecraft.network.chat.Component.literal("Invite cancelled."));
        });

        ctx.setPacketHandled(true);
    }
}
