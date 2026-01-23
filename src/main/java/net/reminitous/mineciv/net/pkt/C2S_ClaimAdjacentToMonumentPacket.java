package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.territory.TerritoryManager;
import net.reminitous.mineciv.net.Network;

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

            if (msg.monumentPos == null || msg.civId == null || msg.dir == null) {
                send(player, false, "Invalid claim request.", -1, null);
                return;
            }

            if (!level.hasChunkAt(msg.monumentPos)) {
                send(player, false, "Monument chunk is not loaded.", -1, null);
                return;
            }

            var be = level.getBlockEntity(msg.monumentPos);
            if (!(be instanceof net.reminitous.mineciv.monument.MonumentBlockEntity monumentBE)) {
                send(player, false, "That is not a valid monument.", -1, null);
                return;
            }

            if (!monumentBE.isBound() || monumentBE.getCivId() == null || !monumentBE.getCivId().equals(msg.civId)) {
                send(player, false, "Monument is not bound to your civilization.", -1, null);
                return;
            }

            CivSavedData data = CivSavedData.get(level.getServer());
            Civilization civ = data.getCiv(msg.civId);
            if (civ == null) {
                send(player, false, "Civilization not found.", -1, null);
                return;
            }

            if (civ.leader() == null || !civ.leader().equals(player.getUUID())) {
                send(player, false, "Only the leader can claim territory.", civ.claimCredits(), null);
                return;
            }

            if (civ.claimCredits() <= 0) {
                send(player, false, "No claim credits available.", civ.claimCredits(), null);
                return;
            }

            // Compute target chunk adjacent to monument chunk
            ChunkPos base = new ChunkPos(msg.monumentPos);
            int dx = 0, dz = 0;
            switch (msg.dir) {
                case NORTH -> dz = -1;
                case SOUTH -> dz =  1;
                case WEST  -> dx = -1;
                case EAST  -> dx =  1;
            }
            ChunkPos target = new ChunkPos(base.x + dx, base.z + dz);

            // Overworld authority for claims
            ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) {
                send(player, false, "Overworld is null.", civ.claimCredits(), null);
                return;
            }

            TerritoryManager.ClaimResult res = TerritoryManager.claimChunkDetailed(overworld, civ.id(), target);
            if (res != TerritoryManager.ClaimResult.SUCCESS) {
                send(player, false,
                        "Failed to claim " + msg.dir + ": " + failureText(res),
                        civ.claimCredits(),
                        target
                );
                return;
            }

            // Spend credit only after successful claim
            civ.spendClaimCredit();
            data.putCiv(civ);

            send(player, true,
                    "Claimed " + msg.dir + " (" + target.x + ", " + target.z + "). Credits left: " + civ.claimCredits(),
                    civ.claimCredits(),
                    target
            );
        });

        ctx.setPacketHandled(true);
    }

    private static void send(ServerPlayer player, boolean success, String message, int credits, ChunkPos chunk) {
        boolean hasChunk = chunk != null;
        int x = hasChunk ? chunk.x : 0;
        int z = hasChunk ? chunk.z : 0;

        Network.CH.send(
                new S2C_ClaimFeedbackPacket(success, message, credits, hasChunk, x, z),
                PacketDistributor.PLAYER.with(player)
        );
    }

    private static String failureText(TerritoryManager.ClaimResult r) {
        return switch (r) {
            case ALREADY_CLAIMED -> "that chunk is already claimed.";
            case CIV_NOT_FOUND -> "civ not found.";
            case MAX_CHUNKS_REACHED -> "max territory size reached (" + TerritoryManager.MAX_CHUNKS + ").";
            case NOT_ADJACENT -> "must touch your territory edge.";
            case TOO_WIDE -> "must fit inside a " + TerritoryManager.MAX_SPAN + "x" + TerritoryManager.MAX_SPAN + " chunk box.";
            case TOO_CLOSE_TO_OTHER_TERRITORY ->
                    "must keep at least " + TerritoryManager.MIN_TERRITORY_GAP + " chunks away from other civilizations' territory.";
            default -> "claim failed.";
        };
    }
}
