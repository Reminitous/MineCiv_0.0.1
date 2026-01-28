package net.reminitous.mineciv.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import java.util.UUID;

public abstract class MineCivNpcBase extends PathfinderMob {

    private static final EntityDataAccessor<String> DATA_ROLE =
            SynchedEntityData.defineId(MineCivNpcBase.class, EntityDataSerializers.STRING);

    protected UUID civId;
    protected BlockPos homeMonument;

    protected MineCivNpcBase(EntityType<? extends MineCivNpcBase> type, Level level) {
        super(type, level);
        this.setPersistenceRequired(); // never despawn
    }

    /** Base attribute builder shared by all civ NPCs. */
    public static AttributeSupplier.Builder createBaseAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.45D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ROLE, "");
    }

    public String getRole() {
        return this.entityData.get(DATA_ROLE);
    }

    protected void setRole(String role) {
        this.entityData.set(DATA_ROLE, role == null ? "" : role);
    }

    public UUID getCivId() {
        return civId;
    }

    public BlockPos getHomeMonument() {
        return homeMonument;
    }

    public void bindToCiv(UUID civId, BlockPos monumentPos) {
        this.civId = civId;
        this.homeMonument = monumentPos;
    }

    /** Friendly-fire protection: do not allow damage to same-civ NPCs. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean result = super.hurt(source, amount);

        if (result && !this.level().isClientSide && this.getCivId() != null) {
            var attacker = source.getEntity();
            if (attacker instanceof net.minecraft.world.entity.player.Player p) {
                var server = this.level().getServer();
                if (server != null) {
                    var data = net.reminitous.mineciv.civ.CivSavedData.get(server);
                    long now = ((net.minecraft.server.level.ServerLevel) this.level()).getGameTime();
                    // 2 minutes at 20 tps = 2400 ticks (tweak)
                    data.markAggressor(this.getCivId(), p.getUUID(), now + 2400L);
                }
            }
        }

        return result;
    }

    @Override
    public boolean removeWhenFarAway(double dist) {
        return false;
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

        String role = getRole();
        if (role != null && !role.isEmpty()) tag.putString("MineCivRole", role);
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
        } else {
            homeMonument = null;
        }

        if (tag.contains("MineCivRole")) setRole(tag.getString("MineCivRole"));
    }
}
