package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.territory.TerritoryManager;

import java.util.HashSet;
import java.util.UUID;

public final class C2S_DisbandCivPacket {

    private final BlockPos monumentPos;
    private final UUID civId;

    public C2S_DisbandCivPacket(BlockPos monumentPos, UUID civId) {
        this.monumentPos = monumentPos;
        this.civId = civId;
    }

    public static void encode(C2S_DisbandCivPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
    }

    public static C2S_DisbandCivPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        return new C2S_DisbandCivPacket(pos, civId);
    }

    public static void handle(C2S_DisbandCivPacket msg, CustomPayloadEvent.Context ctx) {
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

            // Leader-only
            if (civ.leader() == null || !civ.leader().equals(leader.getUUID())) return;

            // Always unclaim using Overworld as the storage authority
            ServerLevel overworld = leader.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) throw new IllegalStateException("Overworld is null");

            // Clear member mappings (copy to avoid concurrent mod)
            var membersCopy = new HashSet<>(civ.members());
            for (UUID memberId : membersCopy) {
                data.setPlayersCiv(memberId, null);
            }

            // Clear pending invites pointing to this civ
            data.pendingInvites().entrySet().removeIf(e -> msg.civId.equals(e.getValue()));

            // Unclaim all chunks for this civ
            TerritoryManager.unclaimAllChunks(overworld, msg.civId);

            // Remove civ record
            data.removeCiv(msg.civId);

            // Unbind monument
            monumentBE.setCivId(null);

            leader.sendSystemMessage(net.minecraft.network.chat.Component.literal("Civilization disbanded."));
        });

        ctx.setPacketHandled(true);
    }
}
