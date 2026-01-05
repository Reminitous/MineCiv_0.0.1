package net.reminitous.mineciv.civ;

import net.reminitous.mineciv.territory.TerritoryManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

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
        if (findPlayerCiv(level, playerId).isPresent()) return false; // already in civ
        return TerritoryManager.getOwnerCivId(level, chunk) == null;    // chunk free
    }

    public static Civilization createCiv(ServerLevel level,
                                         UUID leaderId,
                                         String name,
                                         CivClassType classType,
                                         BlockPos monumentPos) {

        CivSavedData data = CivSavedData.get(level.getServer()); // GLOBAL (Overworld)

        UUID civId = UUID.randomUUID();
        Civilization civ = new Civilization(civId);
        civ.setName(name);
        civ.setClassType(classType);
        civ.setLeader(leaderId);
        civ.addMember(leaderId);
        civ.setHighestMemberCountEver(1);
        civ.setLastActiveEpochMs(System.currentTimeMillis());
        civ.setMonument(level.dimension().location().toString(), monumentPos);

        // Persist civ
        data.putCiv(civ);

        // IMPORTANT: bind player -> civ mapping (this is what your command reads)
        data.setPlayersCiv(leaderId, civId);

        // Claim the monument chunk using Overworld storage authority
        ChunkPos cp = new ChunkPos(monumentPos);
        ServerLevel overworld = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) {
            data.removeCiv(civId);
            data.setPlayersCiv(leaderId, null);
            throw new IllegalStateException("Overworld is null");
        }

        boolean claimed = net.reminitous.mineciv.territory.TerritoryManager.claimChunk(overworld, civId, cp);
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

        // can't be in another civ
        if (findPlayerCiv(level, newMemberId).isPresent()) return false;

        if (civ.members().size() >= maxMembers) return false;

        civ.addMember(newMemberId);
        if (civ.members().size() > civ.highestMemberCountEver()) {
            civ.setHighestMemberCountEver(civ.members().size());
            // TODO: grant “new high member count” XP here
        }

        data.putCiv(civ);
        return true;
    }

    public static boolean removeMember(ServerLevel level, UUID civId, UUID memberId) {
        CivSavedData data = CivSavedData.get(level.getServer());
        Civilization civ = data.getCiv(civId);
        if (civ == null) return false;

        civ.removeMember(memberId);

        // if leader leaves, you can pick a new leader or disband; define your policy
        if (civ.leader() != null && civ.leader().equals(memberId)) {
            // TODO: choose successor or disband
        }

        data.putCiv(civ);
        return true;
    }

    public static void touchActive(ServerLevel level, UUID playerId) {
        findPlayerCiv(level, playerId).ifPresent(civ -> {
            civ.setLastActiveEpochMs(System.currentTimeMillis());
            CivSavedData.get(level.getServer()).putCiv(civ); // GLOBAL, not dimension-scoped
        });
    }

    public static boolean areAllies(ServerLevel level, UUID civA, UUID civB) {
        CivSavedData data = CivSavedData.get(level.getServer());
        Civilization a = data.getCiv(civA);
        Civilization b = data.getCiv(civB);
        if (a == null || b == null) return false;
        return a.relationTo(civB) == RelationType.ALLY && b.relationTo(civA) == RelationType.ALLY;
    }

    public static boolean requestAlliance(ServerLevel level, UUID fromCivId, UUID toCivId) {
        if (fromCivId == null || toCivId == null) return false;
        if (fromCivId.equals(toCivId)) return false;

        CivSavedData data = CivSavedData.get(level.getServer());
        Civilization from = data.getCiv(fromCivId);
        Civilization to = data.getCiv(toCivId);
        if (from == null || to == null) return false;

        // already allies
        if (from.isAlly(toCivId) && to.isAlly(fromCivId)) return false;

        // add incoming request on target
        to.addIncomingAllyRequest(fromCivId);
        data.putCiv(to);
        return true;
    }

    public static boolean acceptAlliance(ServerLevel level, UUID acceptingCivId, UUID fromCivId) {
        if (acceptingCivId == null || fromCivId == null) return false;
        if (acceptingCivId.equals(fromCivId)) return false;

        CivSavedData data = CivSavedData.get(level.getServer());
        Civilization accepting = data.getCiv(acceptingCivId);
        Civilization from = data.getCiv(fromCivId);
        if (accepting == null || from == null) return false;

        if (!accepting.hasIncomingAllyRequest(fromCivId)) return false;

        accepting.removeIncomingAllyRequest(fromCivId);
        accepting.addAlly(fromCivId);
        from.addAlly(acceptingCivId);

        data.putCiv(accepting);
        data.putCiv(from);
        return true;
    }

    public static boolean declineAlliance(ServerLevel level, UUID acceptingCivId, UUID fromCivId) {
        if (acceptingCivId == null || fromCivId == null) return false;

        CivSavedData data = CivSavedData.get(level.getServer());
        Civilization accepting = data.getCiv(acceptingCivId);
        if (accepting == null) return false;

        if (!accepting.hasIncomingAllyRequest(fromCivId)) return false;

        accepting.removeIncomingAllyRequest(fromCivId);
        data.putCiv(accepting);
        return true;
    }

    public static boolean removeAlliance(ServerLevel level, UUID civA, UUID civB) {
        if (civA == null || civB == null) return false;

        CivSavedData data = CivSavedData.get(level.getServer());
        Civilization a = data.getCiv(civA);
        Civilization b = data.getCiv(civB);
        if (a == null || b == null) return false;

        a.removeAlly(civB);
        b.removeAlly(civA);

        data.putCiv(a);
        data.putCiv(b);
        return true;
    }

    public static void awardCivXp(ServerLevel level, UUID playerId, long xp) {
        if (xp <= 0) return;

        CivSavedData data = CivSavedData.get(level.getServer());
        Optional<Civilization> civOpt = findPlayerCiv(level, playerId);
        if (civOpt.isEmpty()) return;

        Civilization civ = civOpt.get();
        int beforeLevel = civ.civLevel();
        civ.addCivXp(xp);

        data.putCiv(civ);

        if (civ.civLevel() > beforeLevel) {
            // Later: trigger expansion unlocks / NPC spawns / announcements
            net.reminitous.mineciv.MineCiv.LOGGER.info("MineCiv: Civ {} leveled up to {}", civ.id(), civ.civLevel());
        }
    }

}
