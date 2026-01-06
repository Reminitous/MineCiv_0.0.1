package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.UUID;

public final class C2S_RenameCivPacket {

    private final BlockPos monumentPos;
    private final UUID civId;
    private final String newName;

    public C2S_RenameCivPacket(BlockPos monumentPos, UUID civId, String newName) {
        this.monumentPos = monumentPos;
        this.civId = civId;
        this.newName = newName;
    }

    public static void encode(C2S_RenameCivPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
        buf.writeUtf(msg.newName, 32);
    }

    public static C2S_RenameCivPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        String name = buf.readUtf(32);
        return new C2S_RenameCivPacket(pos, civId, name);
    }

    public static void handle(C2S_RenameCivPacket msg, CustomPayloadEvent.Context ctx) {
        var sender = ctx.getSender();
        if (!(sender instanceof net.minecraft.server.level.ServerPlayer player)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) return;

            if (!level.hasChunkAt(msg.monumentPos)) return;

            var be = level.getBlockEntity(msg.monumentPos);
            if (!(be instanceof net.reminitous.mineciv.monument.MonumentBlockEntity monumentBE)) return;

            if (!monumentBE.isBound()) return;
            if (!msg.civId.equals(monumentBE.getCivId())) return;

            CivSavedData data = CivSavedData.get(level.getServer());
            Civilization civ = data.getCiv(msg.civId);
            if (civ == null) return;

            // Leader-only
            if (civ.leader() == null || !civ.leader().equals(player.getUUID())) return;

            String clean = msg.newName.trim();
            if (clean.isEmpty()) return;

            civ.setName(clean);
            data.putCiv(civ);
        });

        ctx.setPacketHandled(true);
    }
}
