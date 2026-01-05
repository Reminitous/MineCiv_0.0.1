package net.reminitous.mineciv.civ;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;

import java.util.*;

public final class Civilization {

    private final UUID id;
    private String name;
    private CivClassType classType;

    private UUID leader;
    private final Set<UUID> members = new HashSet<>();
    private final Set<Long> claimedChunks = new HashSet<>();

    // Store dimension as a string like "minecraft:overworld"
    private String monumentDimId;
    private BlockPos monumentPos;

    // Legacy relations map (can keep for future types)
    private final Map<UUID, RelationType> relations = new HashMap<>();

    private int level;
    private long xp;
    private int highestMemberCountEver;
    private long lastActiveEpochMs;

    private long civXp = 0L;
    private int civLevel = 1;

    public long civXp() { return civXp; }
    public int civLevel() { return civLevel; }

    private final Set<UUID> npcIds = new HashSet<>();

    public void addCivXp(long amount) {
        if (amount <= 0) return;
        civXp += amount;
        // Level up loop
        while (civXp >= xpForNextLevel(civLevel)) {
            civXp -= xpForNextLevel(civLevel);
            civLevel++;
        }
        grantCreditsUpToCurrentLevel();
    }

    private static long xpForNextLevel(int level) {
        long l = Math.max(1, level);
        return 250L * l * l;
    }

    public Set<UUID> npcIds() {
        return Collections.unmodifiableSet(npcIds);
    }

    public void addNpcId(UUID id) {
        if (id != null) npcIds.add(id);
    }

    public void removeNpcId(UUID id) {
        if (id != null) npcIds.remove(id);
    }

    private final Set<UUID> allies = new HashSet<>();
    private final Set<UUID> incomingAllyRequests = new HashSet<>();

    private int claimCredits = 0;
    private int lastCreditsGrantedLevel = 0;

    public int claimCredits() { return claimCredits; }
    public int lastCreditsGrantedLevel() { return lastCreditsGrantedLevel; }

    public void grantCreditsUpToCurrentLevel() {
        while (lastCreditsGrantedLevel < civLevel) {
            lastCreditsGrantedLevel++;
            claimCredits += creditsForLevel(lastCreditsGrantedLevel);
        }
    }

    public boolean spendClaimCredit() {
        if (claimCredits <= 0) return false;
        claimCredits--;
        return true;
    }

    private static int creditsForLevel(int level) {
        return 1;
    }

    public Civilization(UUID id) {
        this.id = id;
    }

    public UUID id() { return id; }

    public String name() { return name; }
    public void setName(String name) { this.name = name; }

    public CivClassType classType() { return classType; }
    public void setClassType(CivClassType classType) { this.classType = classType; }

    public UUID leader() { return leader; }
    public void setLeader(UUID leader) { this.leader = leader; }

    public boolean isMember(UUID playerId) { return members.contains(playerId); }
    public Set<UUID> members() { return Collections.unmodifiableSet(members); }
    public void addMember(UUID playerId) { members.add(playerId); }
    public void removeMember(UUID playerId) { members.remove(playerId); }

    public Set<Long> claimedChunks() { return Collections.unmodifiableSet(claimedChunks); }
    public void addClaimedChunk(long chunkLong) { claimedChunks.add(chunkLong); }
    public void removeClaimedChunk(long chunkLong) { claimedChunks.remove(chunkLong); }

    public String monumentDimId() { return monumentDimId; }
    public BlockPos monumentPos() { return monumentPos; }
    public void setMonument(String dimId, BlockPos pos) {
        this.monumentDimId = dimId;
        this.monumentPos = pos;
    }

    // Relation: allies override legacy map for ALLY
    public RelationType relationTo(UUID otherCivId) {
        if (otherCivId == null) return RelationType.NONE;
        if (isAlly(otherCivId)) return RelationType.ALLY;
        return relations.getOrDefault(otherCivId, RelationType.NONE);
    }

    public void setRelation(UUID otherCivId, RelationType relation) {
        if (otherCivId == null) return;
        if (relation == RelationType.NONE) relations.remove(otherCivId);
        else relations.put(otherCivId, relation);
    }

    public int level() { return level; }
    public void setLevel(int level) { this.level = level; }

    public long xp() { return xp; }
    public void setXp(long xp) { this.xp = xp; }

    public int highestMemberCountEver() { return highestMemberCountEver; }
    public void setHighestMemberCountEver(int v) { this.highestMemberCountEver = v; }

    public long lastActiveEpochMs() { return lastActiveEpochMs; }
    public void setLastActiveEpochMs(long v) { this.lastActiveEpochMs = v; }

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.putString("Name", name == null ? "" : name);
        tag.putString("ClassType", (classType == null ? CivClassType.AGRICULTURAL : classType).name());
        tag.putUUID("Leader", leader == null ? new UUID(0L, 0L) : leader);

        tag.putLong("CivXp", civXp);
        tag.putInt("CivLevel", civLevel);
        tag.putInt("ClaimCredits", claimCredits);
        tag.putInt("LastCreditsGrantedLevel", lastCreditsGrantedLevel);

        ListTag membersTag = new ListTag();
        for (UUID m : members) membersTag.add(StringTag.valueOf(m.toString()));
        tag.put("Members", membersTag);

        ListTag chunksTag = new ListTag();
        for (Long cl : claimedChunks) {
            CompoundTag t = new CompoundTag();
            t.putLong("C", cl);
            chunksTag.add(t);
        }
        tag.put("ClaimedChunks", chunksTag);

        if (monumentDimId != null) tag.putString("MonumentDimId", monumentDimId);
        if (monumentPos != null) tag.put("MonumentPos", NbtUtils.writeBlockPos(monumentPos));

        CompoundTag relTag = new CompoundTag();
        for (Map.Entry<UUID, RelationType> e : relations.entrySet()) {
            relTag.putString(e.getKey().toString(), e.getValue().name());
        }
        tag.put("Relations", relTag);

        tag.putInt("Level", level);
        tag.putLong("XP", xp);
        tag.putInt("HighestMemberCountEver", highestMemberCountEver);
        tag.putLong("LastActiveEpochMs", lastActiveEpochMs);

        ListTag alliesList = new ListTag();
        for (UUID u : allies) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", u);
            alliesList.add(t);
        }
        tag.put("Allies", alliesList);

        ListTag reqList = new ListTag();
        for (UUID u : incomingAllyRequests) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", u);
            reqList.add(t);
        }
        tag.put("IncomingAllyRequests", reqList);

        // ---- NPC IDS (SAVE) ----
        ListTag npcs = new ListTag();
        for (UUID u : this.npcIds) {
            CompoundTag t = new CompoundTag();
            t.putUUID("Id", u);
            npcs.add(t);
        }
        tag.put("NpcIds", npcs);

        return tag;
    }

    public static Civilization fromNbt(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        Civilization civ = new Civilization(id);

        civ.name = tag.getString("Name");
        civ.classType = CivClassType.valueOf(tag.getString("ClassType"));

        UUID leader = tag.getUUID("Leader");
        civ.leader = (leader.getLeastSignificantBits() == 0L && leader.getMostSignificantBits() == 0L) ? null : leader;

        ListTag membersTag = tag.getList("Members", 8);
        for (int i = 0; i < membersTag.size(); i++) {
            civ.members.add(UUID.fromString(membersTag.getString(i)));
        }

        ListTag chunksTag = tag.getList("ClaimedChunks", 10);
        for (int i = 0; i < chunksTag.size(); i++) {
            CompoundTag t = chunksTag.getCompound(i);
            civ.claimedChunks.add(t.getLong("C"));
        }

        if (tag.contains("MonumentDimId")) civ.monumentDimId = tag.getString("MonumentDimId");
        if (tag.contains("MonumentPos")) {
            civ.monumentPos = NbtUtils.readBlockPos(tag, "MonumentPos").orElse(null);
        }

        CompoundTag relTag = tag.getCompound("Relations");
        for (String key : relTag.getAllKeys()) {
            civ.relations.put(UUID.fromString(key), RelationType.valueOf(relTag.getString(key)));
        }

        civ.level = tag.getInt("Level");
        civ.xp = tag.getLong("XP");
        civ.highestMemberCountEver = tag.getInt("HighestMemberCountEver");
        civ.lastActiveEpochMs = tag.getLong("LastActiveEpochMs");

        civ.civXp = tag.getLong("CivXp");
        civ.civLevel = Math.max(1, tag.getInt("CivLevel"));
        civ.claimCredits = tag.getInt("ClaimCredits");
        civ.lastCreditsGrantedLevel = tag.getInt("LastCreditsGrantedLevel");

        ListTag alliesList = tag.getList("Allies", 10);
        for (int i = 0; i < alliesList.size(); i++) {
            CompoundTag t = alliesList.getCompound(i);
            civ.addAlly(t.getUUID("Id"));
        }

        ListTag reqList = tag.getList("IncomingAllyRequests", 10);
        for (int i = 0; i < reqList.size(); i++) {
            CompoundTag t = reqList.getCompound(i);
            civ.addIncomingAllyRequest(t.getUUID("Id"));
        }

        // ---- NPC IDS (LOAD) ----
        ListTag npcs = tag.getList("NpcIds", 10);
        for (int i = 0; i < npcs.size(); i++) {
            CompoundTag t = npcs.getCompound(i);
            civ.addNpcId(t.getUUID("Id"));
        }

        return civ;
    }

    public boolean isAlly(UUID otherCivId) {
        return otherCivId != null && allies.contains(otherCivId);
    }

    public Set<UUID> allies() {
        return Collections.unmodifiableSet(allies);
    }

    public void addAlly(UUID otherCivId) {
        if (otherCivId != null) allies.add(otherCivId);
    }

    public void removeAlly(UUID otherCivId) {
        if (otherCivId != null) allies.remove(otherCivId);
    }

    public boolean hasIncomingAllyRequest(UUID fromCivId) {
        return fromCivId != null && incomingAllyRequests.contains(fromCivId);
    }

    public Set<UUID> incomingAllyRequests() {
        return Collections.unmodifiableSet(incomingAllyRequests);
    }

    public void addIncomingAllyRequest(UUID fromCivId) {
        if (fromCivId != null) incomingAllyRequests.add(fromCivId);
    }

    public void removeIncomingAllyRequest(UUID fromCivId) {
        if (fromCivId != null) incomingAllyRequests.remove(fromCivId);
    }
}
