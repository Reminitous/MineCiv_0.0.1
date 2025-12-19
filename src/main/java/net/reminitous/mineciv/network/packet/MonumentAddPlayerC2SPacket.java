package net.reminitous.mineciv.network.packet;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.reminitous.mineciv.block.ChunkClaimManager;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class MonumentAddPlayerC2SPacket {
    private final BlockPos monumentPos;
    private final String playerName;

    public MonumentAddPlayerC2SPacket(BlockPos monumentPos, String playerName) {
        this.monumentPos = monumentPos;
        this.playerName = playerName;
    }

    public MonumentAddPlayerC2SPacket(FriendlyByteBuf buf) {
        this.monumentPos = buf.readBlockPos();
        this.playerName = buf.readUtf();
    }

    public void toBytes(FriendlyByteBuf buf) {
        buf.writeBlockPos(this.monumentPos);
        buf.writeUtf(this.playerName);
    }

    public boolean handle(Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();
            if (sender == null) return;

            int chunkX = monumentPos.getX() >> 4;
            int chunkZ = monumentPos.getZ() >> 4;

            // Verify the sender owns this monument
            ChunkClaimManager.ClaimData claim = ChunkClaimManager.getClaim(sender.level(), chunkX, chunkZ);
            if (claim == null || !claim.ownerUUID.equals(sender.getUUID())) {
                sender.sendSystemMessage(Component.literal("You don't own this monument!"));
                return;
            }

            // Find the player by name
            Optional<GameProfile> targetProfile = sender.getServer().getProfileCache()
                    .get(playerName);

            if (targetProfile.isEmpty()) {
                sender.sendSystemMessage(Component.literal("Player not found: " + playerName));
                return;
            }

            UUID targetUUID = targetProfile.get().getId();

            // Attempt to add the player
            boolean success = ChunkClaimManager.addPlayerAccess(sender.level(), chunkX, chunkZ, targetUUID);

            if (success) {
                sender.sendSystemMessage(Component.literal("Added " + playerName + " to allowed players"));

                // Notify the added player if they're online
                ServerPlayer targetPlayer = sender.getServer().getPlayerList().getPlayer(targetUUID);
                if (targetPlayer != null) {
                    targetPlayer.sendSystemMessage(Component.literal(
                            sender.getName().getString() + " has given you access to their chunk at ["
                                    + chunkX + ", " + chunkZ + "]"));
                }
            } else {
                sender.sendSystemMessage(Component.literal(
                        "Cannot add " + playerName + ": They already have access to another player's chunks"));
            }
        });
        return true;
    }
}