package net.reminitous.mineciv.civ;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.monument.MonumentBlockEntity;
import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.S2C_ForceCloseMineCivUiPacket;
import net.reminitous.mineciv.territory.TerritoryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class CivilizationManager {

    private CivilizationManager() {}

    public static Optional<Civilization> findPlayerCiv(ServerLevel level, UUID playerId) {
        CivSavedData data = CivSavedData.get(level.getServer());
        for (Civilization civ : data.civs().values()) {
            if (civ.isMember(playerId)) return Optional.of(civ);
        }
        return Optional.empty();
    }

    public static boolean canCreateCiv(ServerLevel level, UUID playerId, ChunkPos chunk) {
        if (findPlayerCiv(level, playerId).isPresent()) return false;
        return TerritoryManager.getOwnerCivId(level, chunk) == null;
    }

    public static Civilization createCiv(ServerLevel level,
                                         UUID leaderId,
                                         String name,
                                         CivClassType classType,
                                         BlockPos monumentPos) {

        CivSavedData data = CivSavedData.get(level.getServer());

        UUID civId = UUID.randomUUID();
        Civilization civ = new Civilization(civId);
        civ.setName(name);
        civ.setClassType(classType);
        civ.setLeader(leaderId);
        civ.addMember(leaderId);
        civ.setHighestMemberCountEver(1);
        civ.setLastActiveEpochMs(System.currentTimeMillis());
        civ.setMonument(level.dimension().location().toString(), monumentPos);

        data.putCiv(civ);
        data.setPlayersCiv(leaderId, civId);

        ChunkPos cp = new ChunkPos(monumentPos);
        ServerLevel overworld = level.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) {
            data.removeCiv(civId);
            data.setPlayersCiv(leaderId, null);
            throw new IllegalStateException("Overworld is null");
        }

        boolean claimed = TerritoryManager.claimChunk(overworld, civId, cp);
        if (!claimed) {
            data.removeCiv(civId);
            data.setPlayersCiv(leaderId, null);
            throw new IllegalStateException("Failed to claim initial chunk for civ creation.");
        }

        return civ;
    }

    public static boolean addMember(ServerLevel level, UUID civId, UUID newMemberId, int maxMembers) {
        CivSavedData data = CivSavedData.get(level.getServer());
        Civilization civ = data.getCiv(civId);
        if (civ == null) return false;

        if (findPlayerCiv(level, newMemberId).isPresent()) return false;
        if (civ.members().size() >= maxMembers) return false;

        civ.addMember(newMemberId);

        if (civ.members().size() > civ.highestMemberCountEver()) {
            civ.setHighestMemberCountEver(civ.members().size());
        }

        data.putCiv(civ);
        data.setPlayersCiv(newMemberId, civId);
        return true;
    }

    public static boolean removeMember(ServerLevel level, UUID civId, UUID memberId) {
        CivSavedData data = CivSavedData.get(level.getServer());
        Civilization civ = data.getCiv(civId);
        if (civ == null) return false;

        civ.removeMember(memberId);
        data.putCiv(civ);

        data.setPlayersCiv(memberId, null);
        return true;
    }

    public static void touchActive(ServerLevel level, UUID playerId) {
        findPlayerCiv(level, playerId).ifPresent(civ -> {
            civ.setLastActiveEpochMs(System.currentTimeMillis());
            CivSavedData.get(level.getServer()).putCiv(civ);
        });
    }

    public static boolean areAllies(ServerLevel level, UUID civA, UUID civB) {
        CivSavedData data = CivSavedData.get(level.getServer());
        Civilization a = data.getCiv(civA);
        Civilization b = data.getCiv(civB);
        if (a == null || b == null) return false;
        return a.relationTo(civB) == RelationType.ALLY && b.relationTo(civA) == RelationType.ALLY;
    }

    /* =========================================================
       DISBAND (leader interacts with monument + confirm prompt)
       ========================================================= */

    public static boolean disbandCiv(ServerLevel level, UUID civId, UUID requesterPlayerId, BlockPos monumentPos) {
        if (civId == null || requesterPlayerId == null || monumentPos == null) return false;

        MinecraftServer server = level.getServer();
        CivSavedData data = CivSavedData.get(server);

        Civilization civ = data.getCiv(civId);
        if (civ == null) return false;

        // Must be leader
        if (civ.leader() == null || !civ.leader().equals(requesterPlayerId)) return false;

        // Must match this civ's monument position
        if (civ.monumentPos() == null || !civ.monumentPos().equals(monumentPos)) return false;

        // Must be bound monument BE for this civ (prevents spoof packets)
        if (!level.hasChunkAt(monumentPos)) return false;

        BlockEntity be = level.getBlockEntity(monumentPos);
        if (!(be instanceof MonumentBlockEntity mbe)) return false;
        if (!mbe.isBound()) return false;
        if (mbe.getCivId() == null || !mbe.getCivId().equals(civId)) return false;

        // 0) Close MineCiv UI for all online members BEFORE deleting state
        for (UUID memberId : civ.members()) {
            ServerPlayer p = server.getPlayerList().getPlayer(memberId);
            if (p != null) {
                Network.CH.send(new S2C_ForceCloseMineCivUiPacket(), PacketDistributor.PLAYER.with(p));
            }
        }

        // 1) Broadcast message to all members (online)
        String civName = (civ.name() == null || civ.name().isEmpty()) ? civId.toString() : civ.name();
        var broadcast = net.minecraft.network.chat.Component.literal("🏳 Civilization disbanded: " + civName);

        for (UUID memberId : civ.members()) {
            ServerPlayer p = server.getPlayerList().getPlayer(memberId);
            if (p != null) p.sendSystemMessage(broadcast);
        }

        // 2) Release NPCs as normal villagers (Option 2 replacement)
        List<UUID> npcIds = new ArrayList<>(civ.npcIds());

        for (var dimLevel : server.getAllLevels()) {
            for (UUID npcId : npcIds) {
                var ent = dimLevel.getEntity(npcId);
                if (ent == null) continue;

                if (ent instanceof net.minecraft.world.entity.npc.Villager oldV) {
                    net.minecraft.world.entity.npc.Villager newV =
                            net.minecraft.world.entity.EntityType.VILLAGER.create(dimLevel);

                    if (newV != null) {
                        newV.moveTo(oldV.getX(), oldV.getY(), oldV.getZ(), oldV.getYRot(), oldV.getXRot());
                        newV.setVillagerData(oldV.getVillagerData());
                        newV.setCustomName(null);
                        newV.setCustomNameVisible(false);
                        // Do NOT call setPersistenceRequired() => despawns normally
                        dimLevel.addFreshEntity(newV);
                    }
                    oldV.discard();
                } else {
                    ent.getPersistentData().remove("MineCivCivId");
                    ent.getPersistentData().remove("MineCivRole");
                    ent.getPersistentData().remove("MineCivHomeMonument");
                }
            }
        }

        for (UUID npcId : npcIds) civ.removeNpcId(npcId);

        // 3) Unclaim all claimed chunks (Overworld authority)
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) overworld = level;

        List<Long> claimed = new ArrayList<>(civ.claimedChunks());
        for (long chunkLong : claimed) {
            ChunkPos cp = new ChunkPos(chunkLong);
            TerritoryManager.unclaimChunk(overworld, civId, cp);
        }

        // 4) Remove player->civ mapping for all members
        for (UUID memberId : civ.members()) {
            data.setPlayersCiv(memberId, null);
        }

        // 5) Clear pending invites pointing to this civ
        List<UUID> toClearInvites = new ArrayList<>();
        for (var e : data.pendingInvites().entrySet()) {
            if (civId.equals(e.getValue())) toClearInvites.add(e.getKey());
        }
        for (UUID invited : toClearInvites) {
            data.setPendingInvite(invited, null);
        }

        // 6) Destroy monument block
        level.setBlock(monumentPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);

        // 7) Delete civ record
        data.removeCiv(civId);

        return true;
    }
}
