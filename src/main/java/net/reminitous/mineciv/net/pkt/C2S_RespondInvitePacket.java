package net.reminitous.mineciv.net.pkt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.UUID;

public final class C2S_RespondInvitePacket {

    private final UUID civId;
    private final boolean accept;

    public C2S_RespondInvitePacket(UUID civId, boolean accept) {
        this.civId = civId;
        this.accept = accept;
    }

    public static void encode(C2S_RespondInvitePacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.civId);
        buf.writeBoolean(msg.accept);
    }

    public static C2S_RespondInvitePacket decode(FriendlyByteBuf buf) {
        UUID civId = buf.readUUID();
        boolean accept = buf.readBoolean();
        return new C2S_RespondInvitePacket(civId, accept);
    }

    public static void handle(C2S_RespondInvitePacket msg, CustomPayloadEvent.Context ctx) {
        var sender = ctx.getSender();
        if (!(sender instanceof ServerPlayer player)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) return;

            CivSavedData data = CivSavedData.get(level.getServer());

            UUID pending = data.getPendingInvite(player.getUUID());
            if (pending == null || !pending.equals(msg.civId)) return;

            if (!msg.accept) {
                data.setPendingInvite(player.getUUID(), null);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Invite declined."));
                return;
            }

            // Accept: must not already be in civ
            if (data.getPlayersCiv(player.getUUID()) != null) {
                data.setPendingInvite(player.getUUID(), null);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("You are already in a civilization."));
                return;
            }

            Civilization civ = data.getCiv(msg.civId);
            if (civ == null) {
                data.setPendingInvite(player.getUUID(), null);
                return;
            }

            // v0: enforce max members = 10
            if (civ.members().size() >= 10) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Civilization is full."));
                return;
            }

            civ.addMember(player.getUUID());
            data.putCiv(civ);

            data.setPlayersCiv(player.getUUID(), civ.id());
            data.setPendingInvite(player.getUUID(), null);

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Joined civilization: " + (civ.name() == null ? "" : civ.name())));
        });

        ctx.setPacketHandled(true);
    }
}
