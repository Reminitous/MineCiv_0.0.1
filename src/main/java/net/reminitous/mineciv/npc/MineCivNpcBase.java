package net.reminitous.mineciv.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

import java.util.UUID;

public abstract class MineCivNpcBase extends Villager {

    /* ---------------- Synced data ---------------- */

    private static final EntityDataAccessor<String> DATA_ROLE =
            SynchedEntityData.defineId(MineCivNpcBase.class, EntityDataSerializers.STRING);

    /* ---------------- Persistent data ---------------- */

    protected UUID civId;
    protected BlockPos homeMonument;

    protected MineCivNpcBase(EntityType<? extends Villager> type, Level level) {
        super(type, level);
    }

    /* ---------------- Synced data ---------------- */

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_ROLE, "");
    }

    public String getRole() {
        return this.entityData.get(DATA_ROLE);
    }

    protected void setRole(String role) {
        this.entityData.set(DATA_ROLE, role == null ? "" : role);
    }

    /* ---------------- Civ binding ---------------- */

    public UUID getCivId() {
        return civId;
    }

    public void bindToCiv(UUID civId, BlockPos monumentPos) {
        this.civId = civId;
        this.homeMonument = monumentPos;
    }

    /* ---------------- AI ---------------- */

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(6, new RandomStrollGoal(this, 0.6D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
    }

    /* ---------------- Friendly-fire protection ---------------- */

    @Override
    public boolean isAlliedTo(net.minecraft.world.entity.Entity other) {
        if (other instanceof Player p) {
            // Players in same civ are allies (we’ll enforce later)
            return true;
        }
        return super.isAlliedTo(other);
    }

    /* ---------------- Persistence ---------------- */

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false; // Civ NPCs never despawn naturally
    }

    /* ---------------- NBT ---------------- */

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        if (civId != null) tag.putUUID("MineCivCivId", civId);
        if (homeMonument != null) {
            tag.putInt("MineCivMonX", homeMonument.getX());
            tag.putInt("MineCivMonY", homeMonument.getY());
            tag.putInt("MineCivMonZ", homeMonument.getZ());
        }

        if (!getRole().isEmpty()) {
            tag.putString("MineCivRole", getRole());
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        civId = tag.hasUUID("MineCivCivId") ? tag.getUUID("MineCivCivId") : null;

        if (tag.contains("MineCivMonX")) {
            homeMonument = new BlockPos(
                    tag.getInt("MineCivMonX"),
                    tag.getInt("MineCivMonY"),
                    tag.getInt("MineCivMonZ")
            );
        }

        if (tag.contains("MineCivRole")) {
            setRole(tag.getString("MineCivRole"));
        }
    }
}
