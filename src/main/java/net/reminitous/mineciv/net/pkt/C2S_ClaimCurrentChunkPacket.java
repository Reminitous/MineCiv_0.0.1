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

public final class C2S_ClaimCurrentChunkPacket {

    private final BlockPos monumentPos;
    private final UUID civId;

    public C2S_ClaimCurrentChunkPacket(BlockPos monumentPos, UUID civId) {
        this.monumentPos = monumentPos;
        this.civId = civId;
    }

    public static void encode(C2S_ClaimCurrentChunkPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
    }

    public static C2S_ClaimCurrentChunkPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        return new C2S_ClaimCurrentChunkPacket(pos, civId);
    }

    public static void handle(C2S_ClaimCurrentChunkPacket msg, CustomPayloadEvent.Context ctx) {
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

            ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) throw new IllegalStateException("Overworld is null");

            ChunkPos cp = new ChunkPos(player.blockPosition());

            TerritoryManager.ClaimResult res = TerritoryManager.claimChunkDetailed(overworld, civ.id(), cp);
            if (res != TerritoryManager.ClaimResult.SUCCESS) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal(failureText(res)));
                return;
            }

            // spend after success
            civ.spendClaimCredit();
            data.putCiv(civ);

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "Chunk claimed at " + cp.x + ", " + cp.z + " (Credits left: " + civ.claimCredits() + ")"
            ));
        });

        ctx.setPacketHandled(true);
    }

    private static String failureText(TerritoryManager.ClaimResult r) {
        return switch (r) {
            case ALREADY_CLAIMED -> "That chunk is already claimed.";
            case CIV_NOT_FOUND -> "Your civilization could not be found.";
            case MAX_CHUNKS_REACHED -> "Your civilization is at the max territory size (100 chunks).";
            case NOT_ADJACENT -> "Cannot claim: new chunks must touch your territory edge.";
            case TOO_WIDE -> "Cannot claim: territory must fit inside a 10x10 chunk box.";
            default -> "Claim failed.";
        };
    }
}
