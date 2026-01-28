package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.civ.CivClass;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.monument.MonumentBlockEntity;
import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.registry.ModBlocks;
import net.reminitous.mineciv.territory.TerritoryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class C2S_CreateCivPacket {

    private final String name;
    private final CivClass classType;
    private final BlockPos monumentPos;

    public C2S_CreateCivPacket(String name, CivClass classType, BlockPos monumentPos) {
        this.name = name;
        this.classType = classType;
        this.monumentPos = monumentPos;
    }

    public static void encode(C2S_CreateCivPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name == null ? "" : msg.name, 32);
        buf.writeEnum(msg.classType == null ? CivClass.AGRICULTURAL : msg.classType);
        buf.writeBlockPos(msg.monumentPos);
    }

    public static C2S_CreateCivPacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf(32);
        CivClass type = buf.readEnum(CivClass.class);
        BlockPos pos = buf.readBlockPos();
        return new C2S_CreateCivPacket(name, type, pos);
    }

    public static void handle(C2S_CreateCivPacket msg, CustomPayloadEvent.Context ctx) {
        Object s = ctx.getSender();
        if (!(s instanceof ServerPlayer player)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) return;
            if (msg.monumentPos == null) return;

            // Don’t force-load chunks
            if (!level.hasChunkAt(msg.monumentPos)) return;

            // Must be our monument block
            if (level.getBlockState(msg.monumentPos).getBlock() != ModBlocks.MONUMENT.get()) return;

            // Must have our BE
            var be = level.getBlockEntity(msg.monumentPos);
            if (!(be instanceof MonumentBlockEntity monumentBE)) return;

            // Already bound? don’t recreate
            if (monumentBE.isBound()) return;

            // Must not already be in a civ
            if (CivilizationManager.findPlayerCiv(level, player.getUUID()).isPresent()) return;

            // Creation rules
            ChunkPos chunk = new ChunkPos(msg.monumentPos);
            if (!CivilizationManager.canCreateCiv(level, player.getUUID(), chunk)) return;

            // Sanitize name
            String civName = (msg.name == null) ? "" : msg.name.trim();
            if (civName.isEmpty()) civName = "Civilization";

            CivClass type = (msg.classType == null) ? CivClass.AGRICULTURAL : msg.classType;

            // ---- CREATE CIV (ONCE) ----
            Civilization civ = CivilizationManager.createCiv(
                    level,
                    player.getUUID(),
                    civName,
                    type,
                    msg.monumentPos
            );

            // ---- BIND MONUMENT BE ----
            monumentBE.bindToCiv(civ.id());
            monumentBE.setChanged();

            // Force clients to see BE update
            level.sendBlockUpdated(
                    msg.monumentPos,
                    level.getBlockState(msg.monumentPos),
                    level.getBlockState(msg.monumentPos),
                    3
            );

            // ---- IMMEDIATELY OPEN MANAGE SCREEN (Requirement #1) ----
            CivSavedData data = CivSavedData.get(level.getServer());

            boolean isLeader = civ.leader() != null && civ.leader().equals(player.getUUID());

            List<UUID> members = new ArrayList<>(civ.members());

            // Pending invites from CivSavedData (invitedPlayer -> civId)
            List<UUID> pendingInvites = new ArrayList<>();
            for (Map.Entry<UUID, UUID> e2 : data.pendingInvites().entrySet()) {
                if (civ.id().equals(e2.getValue())) pendingInvites.add(e2.getKey());
            }

            int claimedChunks = civ.claimedChunks().size();
            int maxChunks = TerritoryManager.MAX_CHUNKS;

            // Pending war snapshot defaults (safe even if war system not fully wired)
            boolean hasPendingWar = false;
            UUID pendingWarId = null;
            String pendingPhase = "";
            UUID pendingOpponentCivId = new UUID(0L, 0L);
            String pendingOpponentName = "";
            long pendingStartsAtMs = 0L;
            int pendingPrepMinutes = 0;

            Network.CH.send(
                    new S2C_OpenManageCivScreenPacket(
                            msg.monumentPos,
                            civ.id(),
                            civ.name() == null ? "" : civ.name(),
                            civ.classType() == null ? CivClass.AGRICULTURAL : civ.classType(),
                            civ.civLevel(),
                            civ.civXp(),
                            civ.members().size(),
                            isLeader,
                            members,
                            pendingInvites,
                            civ.claimCredits(),
                            claimedChunks,
                            maxChunks,
                            hasPendingWar,
                            pendingWarId,
                            pendingPhase,
                            pendingOpponentCivId,
                            pendingOpponentName,
                            pendingStartsAtMs,
                            pendingPrepMinutes
                    ),
                    PacketDistributor.PLAYER.with(player)
            );
        });

        ctx.setPacketHandled(true);
    }
}
