package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.civ.CivClassType;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.territory.TerritoryManager;

import net.reminitous.mineciv.war.WarSavedData;
import net.reminitous.mineciv.war.WarState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class C2S_RequestManageCivDataPacket {

    private final BlockPos monumentPos;
    private final UUID civId;

    public C2S_RequestManageCivDataPacket(BlockPos monumentPos, UUID civId) {
        this.monumentPos = monumentPos;
        this.civId = civId;
    }

    public static void encode(C2S_RequestManageCivDataPacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.monumentPos);
        buf.writeUUID(msg.civId);
    }

    public static C2S_RequestManageCivDataPacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        UUID civId = buf.readUUID();
        return new C2S_RequestManageCivDataPacket(pos, civId);
    }

    public static void handle(C2S_RequestManageCivDataPacket msg, CustomPayloadEvent.Context ctx) {
        var sender = ctx.getSender();
        if (!(sender instanceof ServerPlayer player)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) return;

            if (!level.hasChunkAt(msg.monumentPos)) return;

            var be = level.getBlockEntity(msg.monumentPos);
            if (!(be instanceof net.reminitous.mineciv.monument.MonumentBlockEntity monumentBE)) return;

            if (!monumentBE.isBound()) return;
            if (monumentBE.getCivId() == null) return;

            UUID bound = monumentBE.getCivId();
            if (!bound.equals(msg.civId)) return;

            var server = level.getServer();

            CivSavedData data = CivSavedData.get(server);
            Civilization civ = data.getCiv(bound);
            if (civ == null) return;

            boolean isLeader = civ.leader() != null && civ.leader().equals(player.getUUID());

            List<UUID> members = new ArrayList<>(civ.members());

            List<UUID> pending = new ArrayList<>();
            for (Map.Entry<UUID, UUID> e : data.pendingInvites().entrySet()) {
                if (bound.equals(e.getValue())) pending.add(e.getKey());
            }

            int claimedChunks = civ.claimedChunks().size();
            int maxChunks = TerritoryManager.MAX_CHUNKS;

            // ---------------- Pending war snapshot (NEW) ----------------
            boolean hasPendingWar = false;
            UUID pendingWarId = null;
            String pendingPhase = "";
            UUID pendingOpponentCivId = new UUID(0L, 0L);
            String pendingOpponentName = "";
            long pendingStartsAtMs = 0L;
            int pendingPrepMinutes = 0;

            WarSavedData warData = WarSavedData.get(server);
            UUID pw = warData.getPendingWarId(civ.id());
            if (pw != null) {
                WarState w = warData.getWar(pw);
                if (w != null && w.phase() != WarState.Phase.ENDED) {
                    hasPendingWar = true;
                    pendingWarId = w.warId();
                    pendingPhase = w.phase().name();
                    pendingPrepMinutes = w.preparationMinutes();

                    UUID opp = civ.id().equals(w.attackerCivId()) ? w.defenderCivId() : w.attackerCivId();
                    pendingOpponentCivId = (opp == null) ? new UUID(0L, 0L) : opp;

                    Civilization oppCiv = (opp == null) ? null : data.getCiv(opp);
                    pendingOpponentName = (oppCiv != null && oppCiv.name() != null && !oppCiv.name().isBlank())
                            ? oppCiv.name()
                            : String.valueOf(opp);

                    if (w.phase() == WarState.Phase.PREPARING) {
                        pendingStartsAtMs = w.preparationEndsAtMs();
                    } else if (w.phase() == WarState.Phase.PROPOSED) {
                        long a = w.preparationEndsAtMs();
                        long b = w.leaderOnlineDeadlineMs();
                        pendingStartsAtMs = (a <= 0) ? b : (b <= 0 ? a : Math.min(a, b));
                    } else {
                        pendingStartsAtMs = 0L;
                    }
                }
            }

            Network.CH.send(
                    new S2C_OpenManageCivScreenPacket(
                            msg.monumentPos,
                            civ.id(),
                            civ.name() == null ? "" : civ.name(),
                            civ.classType() == null ? CivClassType.AGRICULTURAL : civ.classType(),
                            civ.civLevel(),
                            civ.civXp(),
                            civ.members().size(),
                            isLeader,
                            members,
                            pending,
                            civ.claimCredits(),
                            claimedChunks,
                            maxChunks,

                            // NEW pending war fields:
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
