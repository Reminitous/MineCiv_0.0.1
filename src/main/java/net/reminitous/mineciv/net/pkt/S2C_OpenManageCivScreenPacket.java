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

    public S2C_OpenManageCivScreenPacket(BlockPos monumentPos,
                                         UUID civId,
                                         String civName,
                                         CivClassType classType,
                                         int civLevel,
                                         long civXp,
                                         int memberCount,
                                         boolean isLeader,
                                         List<UUID> members) {
        this.monumentPos = monumentPos;
        this.civId = civId;
        this.civName = civName;
        this.classType = classType;
        this.civLevel = civLevel;
        this.civXp = civXp;
        this.memberCount = memberCount;
        this.isLeader = isLeader;
        this.members = members;
    }

    public static void encode(S2C_OpenManageCivScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
        buf.writeUtf(msg.civName, 32);
        buf.writeEnum(msg.classType);
        buf.writeVarInt(msg.civLevel);
        buf.writeVarLong(msg.civXp);
        buf.writeVarInt(msg.memberCount);
        buf.writeBoolean(msg.isLeader);

        buf.writeVarInt(msg.members.size());
        for (UUID u : msg.members) buf.writeUUID(u);
    }

    public static S2C_OpenManageCivScreenPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        String name = buf.readUtf(32);
        CivClassType type = buf.readEnum(CivClassType.class);
        int lvl = buf.readVarInt();
        long xp = buf.readVarLong();
        int membersCount = buf.readVarInt();
        boolean isLeader = buf.readBoolean();

        int n = buf.readVarInt();
        List<UUID> members = new ArrayList<>(Math.max(0, n));
        for (int i = 0; i < n; i++) members.add(buf.readUUID());

        return new S2C_OpenManageCivScreenPacket(pos, civId, name, type, lvl, xp, membersCount, isLeader, members);
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
