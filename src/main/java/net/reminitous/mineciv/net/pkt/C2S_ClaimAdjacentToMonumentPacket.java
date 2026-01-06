package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.territory.TerritoryManager;

import java.util.UUID;

public final class C2S_ClaimAdjacentToMonumentPacket {

    public enum Dir { NORTH, SOUTH, WEST, EAST }

    private final BlockPos monumentPos;
    private final UUID civId;
    private final Dir dir;

    public C2S_ClaimAdjacentToMonumentPacket(BlockPos monumentPos, UUID civId, Dir dir) {
        this.monumentPos = monumentPos;
        this.civId = civId;
        this.dir = dir;
    }

    public static void encode(C2S_ClaimAdjacentToMonumentPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
        buf.writeEnum(msg.dir);
    }

    public static C2S_ClaimAdjacentToMonumentPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        Dir dir = buf.readEnum(Dir.class);
        return new C2S_ClaimAdjacentToMonumentPacket(pos, civId, dir);
    }

    public static void handle(C2S_ClaimAdjacentToMonumentPacket msg, CustomPayloadEvent.Context ctx) {
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

            if (civ.leader() == null || !civ.leader().equals(player.getUUID())) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Only the leader can claim chunks."));
                return;
            }

            if (civ.claimCredits() <= 0) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("No claim credits available."));
                return;
            }

            ChunkPos base = new ChunkPos(msg.monumentPos);
            int dx = 0, dz = 0;
            switch (msg.dir) {
                case NORTH -> dz = -1;
                case SOUTH -> dz =  1;
                case WEST  -> dx = -1;
                case EAST  -> dx =  1;
            }
            ChunkPos target = new ChunkPos(base.x + dx, base.z + dz);

            ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) throw new IllegalStateException("Overworld is null");

            TerritoryManager.ClaimResult res = TerritoryManager.claimChunkDetailed(overworld, civ.id(), target);
            if (res != TerritoryManager.ClaimResult.SUCCESS) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "Failed to claim " + msg.dir + ": " + failureText(res)
                ));
                return;
            }

            civ.spendClaimCredit();
            data.putCiv(civ);

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Claimed " + msg.dir + " of monument (" + target.x + ", " + target.z + "). Credits left: " + civ.claimCredits()
            ));
        });

        ctx.setPacketHandled(true);
    }

    private static String failureText(TerritoryManager.ClaimResult r) {
        return switch (r) {
            case ALREADY_CLAIMED -> "that chunk is already claimed.";
            case CIV_NOT_FOUND -> "civ not found.";
            case MAX_CHUNKS_REACHED -> "max territory size reached (100).";
            case NOT_ADJACENT -> "must touch your territory edge.";
            case TOO_WIDE -> "must fit inside a 10x10 chunk box.";
            default -> "claim failed.";
        };
    }
}
