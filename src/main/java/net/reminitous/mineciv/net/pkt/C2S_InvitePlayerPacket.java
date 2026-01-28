package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.civ.CivClass;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.net.Network;

import java.util.UUID;

public final class C2S_InvitePlayerPacket {

    private final BlockPos monumentPos;
    private final UUID civId;
    private final String targetName;

    public C2S_InvitePlayerPacket(BlockPos monumentPos, UUID civId, String targetName) {
        this.monumentPos = monumentPos;
        this.civId = civId;
        this.targetName = targetName;
    }

    public static void encode(C2S_InvitePlayerPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
        buf.writeUtf(msg.targetName, 32);
    }

    public static C2S_InvitePlayerPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        String name = buf.readUtf(32);
        return new C2S_InvitePlayerPacket(pos, civId, name);
    }

    public static void handle(C2S_InvitePlayerPacket msg, CustomPayloadEvent.Context ctx) {
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
            if (monumentBE.getCivId() == null || !monumentBE.getCivId().equals(msg.civId)) return;

            CivSavedData data = CivSavedData.get(level.getServer());
            Civilization civ = data.getCiv(msg.civId);
            if (civ == null) return;

            // Leader-only
            if (civ.leader() == null || !civ.leader().equals(leader.getUUID())) return;

            String name = msg.targetName.trim();
            if (name.isEmpty()) return;

            // v0: invited player must be ONLINE
            ServerPlayer target = level.getServer().getPlayerList().getPlayerByName(name);
            if (target == null) {
                leader.sendSystemMessage(net.minecraft.network.chat.Component.literal("Player not found (must be online): " + name));
                return;
            }

            // Can't invite someone already in any civ
            if (data.getPlayersCiv(target.getUUID()) != null) {
                leader.sendSystemMessage(net.minecraft.network.chat.Component.literal("That player is already in a civilization."));
                return;
            }

            // Save pending invite
            data.setPendingInvite(target.getUUID(), civ.id());

            // Send popup now
            Network.CH.send(
                    new S2C_OpenInvitePopupPacket(
                            civ.id(),
                            civ.name() == null ? "" : civ.name(),
                            civ.classType() == null ? CivClass.AGRICULTURAL : civ.classType()
                    ),
                    PacketDistributor.PLAYER.with(target)
            );

            leader.sendSystemMessage(net.minecraft.network.chat.Component.literal("Invite sent to " + target.getGameProfile().getName()));
        });

        ctx.setPacketHandled(true);
    }
}