package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.UUID;

public final class C2S_LeaveCivPacket {

    private final BlockPos monumentPos;
    private final UUID civId;

    public C2S_LeaveCivPacket(BlockPos monumentPos, UUID civId) {
        this.monumentPos = monumentPos;
        this.civId = civId;
    }

    public static void encode(C2S_LeaveCivPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
    }

    public static C2S_LeaveCivPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        return new C2S_LeaveCivPacket(pos, civId);
    }

    public static void handle(C2S_LeaveCivPacket msg, CustomPayloadEvent.Context ctx) {
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

            // Must be a member
            if (!civ.isMember(player.getUUID())) return;

            // v0 policy: leader cannot "leave" (use Disband for now)
            if (civ.leader() != null && civ.leader().equals(player.getUUID())) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Leader cannot leave. Use Disband (or transfer leadership in a future update)."
                ));
                return;
            }

            civ.removeMember(player.getUUID());
            data.putCiv(civ);

            // Clear mapping
            data.setPlayersCiv(player.getUUID(), null);

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You left the civilization."));
        });

        ctx.setPacketHandled(true);
    }
}
