package net.reminitous.mineciv.net.pkt;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;

import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.client.screen.ManageCivScreen;
import net.reminitous.mineciv.civ.CivClassType;

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
    public final int claimedChunks;
    public final int maxChunks;

    // --- NEW: Pending war snapshot for this civ (optional) ---
    public final boolean hasPendingWar;
    public final UUID pendingWarId;
    public final String pendingPhase;          // "PROPOSED", "PREPARING", "ACTIVE", "ENDED"
    public final UUID pendingOpponentCivId;
    public final String pendingOpponentName;
    public final long pendingStartsAtMs;       // For PREPARING: prep end. For PROPOSED: earliest auto-start (leader deadline or force)
    public final int pendingPrepMinutes;

    public S2C_OpenManageCivScreenPacket(
            BlockPos monumentPos,
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
            int maxChunks,
            boolean hasPendingWar,
            UUID pendingWarId,
            String pendingPhase,
            UUID pendingOpponentCivId,
            String pendingOpponentName,
            long pendingStartsAtMs,
            int pendingPrepMinutes
    ) {
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

        this.hasPendingWar = hasPendingWar;
        this.pendingWarId = pendingWarId;
        this.pendingPhase = pendingPhase;
        this.pendingOpponentCivId = pendingOpponentCivId;
        this.pendingOpponentName = pendingOpponentName;
        this.pendingStartsAtMs = pendingStartsAtMs;
        this.pendingPrepMinutes = pendingPrepMinutes;
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
        buf.writeVarInt(msg.claimedChunks);
        buf.writeVarInt(msg.maxChunks);

        // NEW pending war fields
        buf.writeBoolean(msg.hasPendingWar);
        if (msg.hasPendingWar) {
            buf.writeUUID(msg.pendingWarId);
            buf.writeUtf(msg.pendingPhase == null ? "" : msg.pendingPhase, 16);
            buf.writeUUID(msg.pendingOpponentCivId);
            buf.writeUtf(msg.pendingOpponentName == null ? "" : msg.pendingOpponentName, 64);
            buf.writeVarLong(msg.pendingStartsAtMs);
            buf.writeVarInt(msg.pendingPrepMinutes);
        }
    }

    public static S2C_OpenManageCivScreenPacket decode(FriendlyByteBuf buf) {
        BlockPos monumentPos = buf.readBlockPos();

        UUID civId = buf.readUUID();
        String civName = buf.readUtf(32);
        CivClassType classType = buf.readEnum(CivClassType.class);

        int civLevel = buf.readVarInt();
        long civXp = buf.readVarLong();

        int memberCount = buf.readVarInt();
        boolean isLeader = buf.readBoolean();

        int mSize = buf.readVarInt();
        List<UUID> members = new ArrayList<>();
        for (int i = 0; i < mSize; i++) members.add(buf.readUUID());

        int pSize = buf.readVarInt();
        List<UUID> pendingInvites = new ArrayList<>();
        for (int i = 0; i < pSize; i++) pendingInvites.add(buf.readUUID());

        int claimCredits = buf.readVarInt();
        int claimedChunks = buf.readVarInt();
        int maxChunks = buf.readVarInt();

        boolean hasPendingWar = buf.readBoolean();
        UUID pendingWarId = null;
        String pendingPhase = "";
        UUID pendingOpponentCivId = new UUID(0L, 0L);
        String pendingOpponentName = "";
        long pendingStartsAtMs = 0L;
        int pendingPrepMinutes = 0;

        if (hasPendingWar) {
            pendingWarId = buf.readUUID();
            pendingPhase = buf.readUtf(16);
            pendingOpponentCivId = buf.readUUID();
            pendingOpponentName = buf.readUtf(64);
            pendingStartsAtMs = buf.readVarLong();
            pendingPrepMinutes = buf.readVarInt();
        }

        return new S2C_OpenManageCivScreenPacket(
                monumentPos,
                civId,
                civName,
                classType,
                civLevel,
                civXp,
                memberCount,
                isLeader,
                members,
                pendingInvites,
                claimCredits,
                claimedChunks,
                maxChunks,
                hasPendingWar,
                pendingWarId,
                pendingPhase,
                pendingOpponentCivId,
                pendingOpponentName,
                pendingStartsAtMs,
                pendingPrepMinutes
        );
    }

    public static void handle(S2C_OpenManageCivScreenPacket msg, CustomPayloadEvent.Context ctx) {
        ctx.enqueueWork(() -> Minecraft.getInstance().setScreen(new ManageCivScreen(msg)));
        ctx.setPacketHandled(true);
    }
}
