package net.reminitous.mineciv.npc;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;
import java.util.UUID;

public abstract class MineCivNpcBase extends PathfinderMob {

    private static final EntityDataAccessor<java.util.Optional<UUID>> DATA_CIV_ID =
            SynchedEntityData.defineId(MineCivNpcBase.class, EntityDataSerializers.OPTIONAL_UUID);

    protected MineCivNpcBase(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(false);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_CIV_ID, java.util.Optional.empty());
    }

    public @Nullable UUID getCivId() {
        return this.entityData.get(DATA_CIV_ID).orElse(null);
    }

    public void setCivId(@Nullable UUID civId) {
        this.entityData.set(DATA_CIV_ID, java.util.Optional.ofNullable(civId));
        this.setPersistenceRequired(); // keep civ NPCs from natural despawn
    }

    /** Each role equips its “default kit” here. */
    protected abstract void equipDefaultKit();

    /** Enforce kit if player stripped it, etc. */
    protected final void ensureMainhand(ItemStack desired) {
        ItemStack cur = this.getItemBySlot(EquipmentSlot.MAINHAND);
        if (cur.isEmpty()) {
            this.setItemSlot(EquipmentSlot.MAINHAND, desired.copy());
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();

        // Every ~5 seconds on server: ensure role kit still present
        if (!this.level().isClientSide && this.tickCount % 100 == 0) {
            this.equipDefaultKit();
        }
    }

    @Override
    public @Nullable net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType reason,
            @Nullable net.minecraft.world.entity.SpawnGroupData spawnData,
            @Nullable CompoundTag dataTag
    ) {
        net.minecraft.world.entity.SpawnGroupData out = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
        this.equipDefaultKit();
        return out;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        UUID civ = getCivId();
        if (civ != null) tag.putUUID("MineCivCivId", civ);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.hasUUID("MineCivCivId")) {
            setCivId(tag.getUUID("MineCivCivId"));
        }
    }
}
