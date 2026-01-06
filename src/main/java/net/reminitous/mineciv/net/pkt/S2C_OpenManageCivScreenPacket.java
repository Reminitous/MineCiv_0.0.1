package net.reminitous.mineciv.net.pkt;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivClassType;
import net.reminitous.mineciv.client.screen.ManageCivScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class S2C_OpenManageCivScreenPacket {

    public final BlockPos monumentPos;
    public final UUID civId;
    public final String civName;
    public final CivClassType classType;
    public final int civLevel;
    public final long civXp;
    public final int memberCount;
    public final boolean isLeader;
    public final List<UUID> members;
    public final List<UUID> pendingInvites;

    public final int claimCredits;

    // NEW: territory size info
    public final int claimedChunks;
    public final int maxChunks;

    public S2C_OpenManageCivScreenPacket(BlockPos monumentPos,
                                         UUID civId,
                                         String civName,
                                         CivClassType classType,
                                         int civLevel,
                                         long civXp,
                                         int memberCount,
                                         boolean isLeader,
                                         List<UUID> members,
                                         List<UUID> pendingInvites,
                                         int claimCredits,
                                         int claimedChunks,
                                         int maxChunks) {
        this.monumentPos = monumentPos;
        this.civId = civId;
        this.civName = civName;
        this.classType = classType;
        this.civLevel = civLevel;
        this.civXp = civXp;
        this.memberCount = memberCount;
        this.isLeader = isLeader;
        this.members = members;
        this.pendingInvites = pendingInvites;
        this.claimCredits = claimCredits;
        this.claimedChunks = claimedChunks;
        this.maxChunks = maxChunks;
    }

    public static void encode(S2C_OpenManageCivScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
        buf.writeUtf(msg.civName == null ? "" : msg.civName, 32);
        buf.writeEnum(msg.classType == null ? CivClassType.AGRICULTURAL : msg.classType);

        buf.writeVarInt(msg.civLevel);
        buf.writeVarLong(msg.civXp);
        buf.writeVarInt(msg.memberCount);
        buf.writeBoolean(msg.isLeader);

        buf.writeVarInt(msg.members.size());
        for (UUID u : msg.members) buf.writeUUID(u);

        buf.writeVarInt(msg.pendingInvites.size());
        for (UUID u : msg.pendingInvites) buf.writeUUID(u);

        buf.writeVarInt(msg.claimCredits);

        // NEW
        buf.writeVarInt(msg.claimedChunks);
        buf.writeVarInt(msg.maxChunks);
    }

    public static S2C_OpenManageCivScreenPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        String name = buf.readUtf(32);
        CivClassType type = buf.readEnum(CivClassType.class);

        int lvl = buf.readVarInt();
        long xp = buf.readVarLong();
        int memberCount = buf.readVarInt();
        boolean isLeader = buf.readBoolean();

        int n = buf.readVarInt();
        List<UUID> members = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) members.add(buf.readUUID());

        int p = buf.readVarInt();
        List<UUID> pending = new ArrayList<>(Math.max(0, p));
        for (int i = 0; i < p; i++) pending.add(buf.readUUID());

        int credits = buf.readVarInt();

        // NEW
        int claimedChunks = buf.readVarInt();
        int maxChunks = buf.readVarInt();

        return new S2C_OpenManageCivScreenPacket(
                pos,
                civId,
                name,
                type,
                lvl,
                xp,
                memberCount,
                isLeader,
                members,
                pending,
                credits,
                claimedChunks,
                maxChunks
        );
    }

    public static void handle(S2C_OpenManageCivScreenPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player == null) return;
            mc.setScreen(new ManageCivScreen(msg));
        });
        ctx.setPacketHandled(true);
    }
}
