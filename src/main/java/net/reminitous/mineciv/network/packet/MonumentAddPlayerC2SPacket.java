package net.reminitous.mineciv.network.packet;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.handling.IPayloadContext;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.block.ChunkClaimManager;

import java.util.Optional;
import java.util.UUID;

public record MonumentAddPlayerC2SPayload(
        BlockPos monumentPos,
        String playerName
) implements CustomPacketPayload {

    public static final Type<MonumentAddPlayerC2SPayload> TYPE =
            new Type<>(new ResourceLocation(MineCiv.MODID, "monument_add_player"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MonumentAddPlayerC2SPayload> STREAM_CODEC =
            StreamCodec.of(
                    MonumentAddPlayerC2SPayload::encode,
                    MonumentAddPlayerC2SPayload::decode
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    private static void encode(RegistryFriendlyByteBuf buf, MonumentAddPlayerC2SPayload payload) {
        buf.writeBlockPos(payload.monumentPos);
        buf.writeUtf(payload.playerName);
    }

    private static MonumentAddPlayerC2SPayload decode(RegistryFriendlyByteBuf buf) {
        return new MonumentAddPlayerC2SPayload(
                buf.readBlockPos(),
                buf.readUtf()
        );
    }

    public static void handle(MonumentAddPlayerC2SPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer sender = (ServerPlayer) context.player();
            if (sender == null) return;

            int chunkX = payload.monumentPos.getX() >> 4;
            int chunkZ = payload.monumentPos.getZ() >> 4;

            // Verify ownership
            ChunkClaimManager.ClaimData claim =
                    ChunkClaimManager.getClaim(sender.level(), chunkX, chunkZ);

            if (claim == null || !claim.ownerUUID.equals(sender.getUUID())) {
                sender.sendSystemMessage(Component.literal("You don't own this monument!"));
                return;
            }

            // Find target player profile
            Optional<GameProfile> targetProfile =
                    sender.getServer().getProfileCache().get(payload.playerName);

            if (targetProfile.isEmpty()) {
                sender.sendSystemMessage(
                        Component.literal("Player not found: " + payload.playerName));
                return;
            }

            UUID targetUUID = targetProfile.get().getId();

            boolean success = ChunkClaimManager.addPlayerAccess(
                    sender.level(), chunkX, chunkZ, targetUUID);

            if (success) {
                sender.sendSystemMessage(
                        Component.literal("Added " + payload.playerName + " to allowed players"));

                ServerPlayer targetPlayer =
                        sender.getServer().getPlayerList().getPlayer(targetUUID);

                if (targetPlayer != null) {
                    targetPlayer.sendSystemMessage(Component.literal(
                            sender.getName().getString()
                                    + " has given you access to their chunk at ["
                                    + chunkX + ", " + chunkZ + "]"));
                }
            } else {
                sender.sendSystemMessage(Component.literal(
                        "Cannot add " + payload.playerName
                                + ": They already have access to another player's chunks"));
            }
        });
    }
}
