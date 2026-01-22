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
import net.reminitous.mineciv.monument.MonumentBlockEntity;

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
        return new C2S_ClaimAdjacentToMonumentPacket(
                buf.readBlockPos(),
                buf.readUUID(),
                buf.readEnum(Dir.class)
        );
    }

    public static void handle(C2S_ClaimAdjacentToMonumentPacket msg, CustomPayloadEvent.Context ctx) {
        if (!(ctx.getSender() instanceof ServerPlayer player)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) return;
            if (msg.monumentPos == null || msg.civId == null || msg.dir == null) return;
            if (!level.hasChunkAt(msg.monumentPos)) return;

            if (!(level.getBlockEntity(msg.monumentPos) instanceof MonumentBlockEntity monumentBE)) return;
            if (!monumentBE.isBound()) return;
            if (!msg.civId.equals(monumentBE.getCivId())) return;

            CivSavedData data = CivSavedData.get(level.getServer());
            Civilization civ = data.getCiv(msg.civId);
            if (civ == null) return;

            if (!player.getUUID().equals(civ.leader())) {
                send(player, "Only the civilization leader can claim territory.", 0xFF5555);
                return;
            }

            if (civ.claimCredits() <= 0) {
                send(player, "No claim credits available.", 0xFF5555);
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

            // Overworld authority
            ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) {
                send(player, "Overworld unavailable.", 0xFF5555);
                return;
            }

            TerritoryManager.ClaimResult res =
                    TerritoryManager.claimChunkDetailed(overworld, civ.id(), target);

            if (res != TerritoryManager.ClaimResult.SUCCESS) {
                send(player, "Claim failed: " + failureText(res), 0xFF5555);
                return;
            }

            // Spend credit only on success
            civ.spendClaimCredit();
            data.putCiv(civ);

            send(player,
                    "Claimed " + msg.dir +
                            " (" + target.x + ", " + target.z + "). " +
                            "Credits left: " + civ.claimCredits(),
                    0x55FF55
            );
        });

        ctx.setPacketHandled(true);
    }

    /* ------------------------------------------------------------ */

    private static void send(ServerPlayer player, String text, int color) {
        net.reminitous.mineciv.net.Network.CH.send(
                new S2C_ClaimFeedbackPacket(text, color, 80),
                PacketDistributor.PLAYER.with(player)
        );
    }

    private static String failureText(TerritoryManager.ClaimResult r) {
        return switch (r) {
            case ALREADY_CLAIMED -> "that chunk is already claimed.";
            case CIV_NOT_FOUND -> "civilization not found.";
            case MAX_CHUNKS_REACHED -> "maximum territory size reached.";
            case NOT_ADJACENT -> "territory must be adjacent.";
            case TOO_WIDE -> "territory shape would be too wide.";
            case TOO_CLOSE_TO_OTHER_TERRITORY ->
                    "must keep at least " + TerritoryManager.MIN_TERRITORY_GAP +
                            " chunks from another civilization.";
            default -> "claim failed.";
        };
    }
}
