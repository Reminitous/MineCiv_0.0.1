package net.reminitous.mineciv.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class MineCivNpcBase extends Villager {

    // Synced role string (ex: "archer", "knight", "farmer")
    private static final EntityDataAccessor<String> DATA_ROLE =
            SynchedEntityData.defineId(MineCivNpcBase.class, EntityDataSerializers.STRING);

    // Civ id + home monument stored in NBT
    private UUID civId;
    private BlockPos homeMonument;

    public MineCivNpcBase(EntityType<? extends Villager> type, Level level) {
        super(type, level);
    }

    /* ---------------- Synced data (1.21.x) ---------------- */

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, "");
    }

    public String getRole() {
        return this.entityData.get(DATA_ROLE);
    }

    public void setRole(String role) {
        this.entityData.set(DATA_ROLE, role == null ? "" : role);
    }

    public UUID getCivId() {
        return civId;
    }

    public void setCivId(UUID civId) {
        this.civId = civId;
    }

    public BlockPos getHomeMonument() {
        return homeMonument;
    }

    public void setHomeMonument(BlockPos homeMonument) {
        this.homeMonument = homeMonument;
    }

    /** Convenience bind method */
    public void bindToCiv(UUID civId, @Nullable BlockPos monumentPos) {
        this.civId = civId;
        this.homeMonument = monumentPos;
    }

    /* ---------------- Spawn hook: equip + init ---------------- */

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level,
                                        DifficultyInstance difficulty,
                                        MobSpawnType reason,
                                        @Nullable SpawnGroupData spawnData,
                                        @Nullable CompoundTag tag) {

        SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, spawnData, tag);

        // Only do server-side setup (avoid client desync / double equips)
        if (this.level() instanceof ServerLevel) {
            equipRoleKit();
        }

        return out;
    }

    /**
     * Subclasses override this to equip their default items (bow, sword, hoe, etc).
     * Called exactly once after spawn.
     */
    protected void equipRoleKit() {
        // default: nothing
    }

    protected final void ensureMainHand(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        this.setItemSlot(EquipmentSlot.MAINHAND, stack);
        // Don’t drop free gear
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0f);
    }

    protected final void ensureOffHand(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        this.setItemSlot(EquipmentSlot.OFFHAND, stack);
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0f);
    }

    /* ---------------- NBT ---------------- */

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        String role = getRole();
        if (!role.isEmpty()) tag.putString("MineCivRole", role);

        if (civId != null) tag.putUUID("MineCivCivId", civId);

        if (homeMonument != null) {
            tag.putInt("MineCivMonX", homeMonument.getX());
            tag.putInt("MineCivMonY", homeMonument.getY());
            tag.putInt("MineCivMonZ", homeMonument.getZ());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("MineCivRole")) setRole(tag.getString("MineCivRole"));
        civId = tag.hasUUID("MineCivCivId") ? tag.getUUID("MineCivCivId") : null;

        if (tag.contains("MineCivMonX") && tag.contains("MineCivMonY") && tag.contains("MineCivMonZ")) {
            homeMonument = new BlockPos(
                    tag.getInt("MineCivMonX"),
                    tag.getInt("MineCivMonY"),
                    tag.getInt("MineCivMonZ")
            );
        } else {
            homeMonument = null;
        }
    }
}
