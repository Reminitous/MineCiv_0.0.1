package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.UUID;

public final class C2S_KickMemberPacket {

    private final BlockPos monumentPos;
    private final UUID civId;
    private final UUID targetPlayerId;

    public C2S_KickMemberPacket(BlockPos monumentPos, UUID civId, UUID targetPlayerId) {
        this.monumentPos = monumentPos;
        this.civId = civId;
        this.targetPlayerId = targetPlayerId;
    }

    public static void encode(C2S_KickMemberPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
        buf.writeUUID(msg.targetPlayerId);
    }

    public static C2S_KickMemberPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        UUID target = buf.readUUID();
        return new C2S_KickMemberPacket(pos, civId, target);
    }

    public static void handle(C2S_KickMemberPacket msg, CustomPayloadEvent.Context ctx) {
        var sender = ctx.getSender();
        if (!(sender instanceof ServerPlayer player)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) return;

            if (!level.hasChunkAt(msg.monumentPos)) return;

            var be = level.getBlockEntity(msg.monumentPos);
            if (!(be instanceof net.reminitous.mineciv.monument.MonumentBlockEntity monumentBE)) return;

            if (!monumentBE.isBound()) return;
            if (monumentBE.getCivId() == null) return;
            if (!monumentBE.getCivId().equals(msg.civId)) return;

            CivSavedData data = CivSavedData.get(level.getServer());
            Civilization civ = data.getCiv(msg.civId);
            if (civ == null) return;

            // Leader-only
            if (civ.leader() == null || !civ.leader().equals(player.getUUID())) return;

            // Cannot kick yourself
            if (msg.targetPlayerId.equals(player.getUUID())) return;

            // Must be a member
            if (!civ.isMember(msg.targetPlayerId)) return;

            // Do not allow kicking leader in v0
            if (civ.leader().equals(msg.targetPlayerId)) return;

            civ.removeMember(msg.targetPlayerId);
            data.putCiv(civ);

            // IMPORTANT: clear player -> civ mapping so protections/commands update
            data.setPlayersCiv(msg.targetPlayerId, null);

            ServerPlayer kicked = level.getServer().getPlayerList().getPlayer(msg.targetPlayerId);
            if (kicked != null) {
                kicked.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "You were kicked from civilization: " + (civ.name() == null ? "" : civ.name())
                ));
            }

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Member kicked."));
        });

        ctx.setPacketHandled(true);
    }
}
