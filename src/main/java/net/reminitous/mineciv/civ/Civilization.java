package net.reminitous.mineciv.civ;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;

import net.reminitous.mineciv.npc.NpcRoleType;

import java.util.*;

public final class Civilization {

    private final UUID id;
    private String name;
    private CivClass classType;

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

    // ---------------- NEW: leader-chosen desired NPC composition ----------------
    // Desired counts per role (leader controlled). Only roles allowed by classType are honored.
    private final EnumMap<NpcRoleType, Integer> desiredNpcCounts = new EnumMap<>(NpcRoleType.class);

    // Hard max cap (final clamp) - tweak as you like.
    private static final int MAX_NPC_CAP = 15;

    /** Total NPC cap for this civ based on civLevel, with a reasonable max. */
    public int npcCap() {
        // Example: 3, 6, 9, 12, 15...
        int cap = 3 * Math.max(1, civLevel);
        return Math.min(cap, MAX_NPC_CAP);
    }

    /** Returns a copy of desired counts. */
    public Map<NpcRoleType, Integer> desiredNpcCounts() {
        return Collections.unmodifiableMap(desiredNpcCounts);
    }

    /** Get desired count for a role (0 if not set). */
    public int desiredCount(NpcRoleType role) {
        if (role == null) return 0;
        return Math.max(0, desiredNpcCounts.getOrDefault(role, 0));
    }

    /**
     * Set desired count for a role. (Call from leader commands/UI.)
     * This does NOT spawn instantly; the spawn manager will maintain counts on tick.
     */
    public void setDesiredCount(NpcRoleType role, int count) {
        if (role == null) return;
        desiredNpcCounts.put(role, Math.max(0, count));
        clampDesiredToCapAndClass();
    }

    /** Wipes desired counts not allowed by this civ's class, and clamps totals to cap. */
    public void clampDesiredToCapAndClass() {
        CivClass ct = (classType == null) ? CivClass.AGRICULTURAL : classType;
        Set<NpcRoleType> allowed = NpcRoleType.allowedFor(ct);

        // Remove disallowed roles
        desiredNpcCounts.keySet().removeIf(r -> !allowed.contains(r));

        // Clamp totals to cap
        int cap = npcCap();
        int total = 0;
        for (NpcRoleType r : allowed) total += desiredCount(r);

        if (total <= cap) return;

        // Reduce counts deterministically in a stable order until total fits cap.
        // (Later you can reduce the "largest" first, or reduce non-core roles first.)
        for (NpcRoleType r : allowed) {
            int v = desiredCount(r);
            while (v > 0 && total > cap) {
                v--;
                total--;
            }
            desiredNpcCounts.put(r, v);
            if (total <= cap) break;
        }
    }

    /**
     * If leader hasn't chosen anything yet, we can provide a default distribution
     * so civs aren't empty.
     */
    public void ensureDefaultDesiredIfEmpty() {
        clampDesiredToCapAndClass();

        int sum = 0;
        for (int v : desiredNpcCounts.values()) sum += Math.max(0, v);
        if (sum > 0) return;

        CivClass ct = (classType == null) ? CivClass.AGRICULTURAL : classType;
        int cap = npcCap();

        // Very simple defaults:
        switch (ct) {
            case AGRICULTURAL -> {
                // farmers heavy
                desiredNpcCounts.put(NpcRoleType.FARMER, Math.max(1, cap - 1));
                desiredNpcCounts.put(NpcRoleType.LUMBERJACK, Math.min(1, cap));
                desiredNpcCounts.put(NpcRoleType.SHEPHERD, Math.min(1, Math.max(0, cap - (cap - 1) - 1)));
            }
            case WARLIKE -> {
                desiredNpcCounts.put(NpcRoleType.PATROL, Math.max(1, cap - 2));
                desiredNpcCounts.put(NpcRoleType.KNIGHT, Math.min(1, cap));
                desiredNpcCounts.put(NpcRoleType.ARCHER, Math.min(1, Math.max(0, cap - (cap - 2) - 1)));
            }
            case TECHNOLOGY -> {
                desiredNpcCounts.put(NpcRoleType.MINER, Math.max(1, cap - 1));
                desiredNpcCounts.put(NpcRoleType.WORKER, Math.min(1, cap));
            }
            case MYSTIC -> {
                desiredNpcCounts.put(NpcRoleType.WIZARD, Math.max(1, cap - 2));
                desiredNpcCounts.put(NpcRoleType.WITCH, Math.min(1, cap));
                desiredNpcCounts.put(NpcRoleType.ENCHANTER, Math.min(1, Math.max(0, cap - (cap - 2) - 1)));
            }
        }

        clampDesiredToCapAndClass();
    }

    public void addCivXp(long amount) {
        if (amount <= 0) return;
        civXp += amount;
        // Level up loop
        while (civXp >= xpForNextLevel(civLevel)) {
            civXp -= xpForNextLevel(civLevel);
            civLevel++;
        }
        grantCreditsUpToCurrentLevel();

        // NEW: cap may change when level changes, so clamp desired counts
        clampDesiredToCapAndClass();
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

    public CivClass classType() { return classType; }
    public void setClassType(CivClass classType) {
        this.classType = classType;
        // NEW: class changed => clamp desired to allowed roles
        clampDesiredToCapAndClass();
    }

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
        tag.putString("ClassType", (classType == null ? CivClass.AGRICULTURAL : classType).name());
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

        // ---- NEW: Desired NPC composition (SAVE) ----
        // Stored as list of {Role:"FARMER", Count:3}
        ListTag desired = new ListTag();
        for (Map.Entry<NpcRoleType, Integer> e : desiredNpcCounts.entrySet()) {
            if (e.getKey() == null) continue;
            int c = Math.max(0, e.getValue() == null ? 0 : e.getValue());
            CompoundTag t = new CompoundTag();
            t.putString("Role", e.getKey().name());
            t.putInt("Count", c);
            desired.add(t);
        }
        tag.put("DesiredNpcCounts", desired);

        return tag;
    }

    public static Civilization fromNbt(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        Civilization civ = new Civilization(id);

        civ.name = tag.getString("Name");
        civ.classType = CivClass.valueOf(tag.getString("ClassType"));

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
        civ.grantCreditsUpToCurrentLevel();

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

        // ---- NEW: Desired NPC composition (LOAD) ----
        ListTag desired = tag.getList("DesiredNpcCounts", 10);
        for (int i = 0; i < desired.size(); i++) {
            CompoundTag t = desired.getCompound(i);
            if (!t.contains("Role")) continue;
            String roleName = t.getString("Role");
            int count = t.getInt("Count");
            try {
                NpcRoleType role = NpcRoleType.valueOf(roleName);
                civ.desiredNpcCounts.put(role, Math.max(0, count));
            } catch (Exception ignored) {}
        }

        // Ensure legality after load
        civ.clampDesiredToCapAndClass();
        civ.ensureDefaultDesiredIfEmpty();

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
