package net.reminitous.mineciv.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

public abstract class MineCivNpcBase extends PathfinderMob {

    /* ---------------- Synced data ---------------- */

    private static final EntityDataAccessor<Optional<UUID>> CIV_ID =
            SynchedEntityData.defineId(MineCivNpcBase.class, EntityDataSerializers.OPTIONAL_UUID);

    private static final EntityDataAccessor<BlockPos> MONUMENT_POS =
            SynchedEntityData.defineId(MineCivNpcBase.class, EntityDataSerializers.BLOCK_POS);

    private static final EntityDataAccessor<String> ROLE =
            SynchedEntityData.defineId(MineCivNpcBase.class, EntityDataSerializers.STRING);

    protected MineCivNpcBase(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setPersistenceRequired();
    }

    /* ---------------- Attributes ---------------- */

    /** Required for all PathfinderMob subclasses to define default attributes */
    public static AttributeSupplier.Builder createBaseAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    /* ---------------- Init ---------------- */

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(CIV_ID, Optional.empty());
        builder.define(MONUMENT_POS, BlockPos.ZERO);
        builder.define(ROLE, "WORKER");
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new ReturnToCivBorderGoal(this, 1.2D));
        this.goalSelector.addGoal(2, new RoleAwareWanderGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new RandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(4, new LookAtPlayerGoal(this, net.minecraft.world.entity.player.Player.class, 6.0F));
        this.goalSelector.addGoal(5, new RandomLookAroundGoal(this));
    }

    /* ---------------- Civ binding ---------------- */

    public void bindToCiv(UUID civId, BlockPos monument) {
        this.entityData.set(CIV_ID, Optional.ofNullable(civId));
        this.entityData.set(MONUMENT_POS, monument);
    }

    public void setRole(String role) {
        this.entityData.set(ROLE, role == null ? "" : role);
    }

    /* ---------------- Civ getters ---------------- */

    /** Returns the role of this NPC (now public so renderers can access it) */
    public String getRole() {
        return entityData.get(ROLE);
    }

    /** Returns the UUID of the civilization this NPC belongs to */
    public UUID getCivId() {
        return entityData.get(CIV_ID).orElse(null);
    }

    /** Returns the monument (home) position of the civilization this NPC belongs to */
    public BlockPos getHomeMonument() {
        return entityData.get(MONUMENT_POS);
    }


    protected Optional<Civilization> getCiv() {
        if (!(level() instanceof ServerLevel server)) return Optional.empty();
        Optional<UUID> id = entityData.get(CIV_ID);
        if (id.isEmpty()) return Optional.empty();
        return Optional.ofNullable(CivSavedData.get(server.getServer()).getCiv(id.get()));
    }

    protected BlockPos getMonument() {
        return entityData.get(MONUMENT_POS);
    }

    protected int getCivRadius() {
        return getCiv()
                .map(c -> Math.max(16, c.claimedChunks().size() * 4))
                .orElse(16);
    }

    /* ---------------- Persistence ---------------- */

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        entityData.get(CIV_ID).ifPresent(id -> tag.putUUID("MineCivCivId", id));
        tag.putLong("MineCivMonument", getMonument().asLong());
        tag.putString("MineCivRole", getRole());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("MineCivCivId")) {
            entityData.set(CIV_ID, Optional.of(tag.getUUID("MineCivCivId")));
        }
        if (tag.contains("MineCivMonument")) {
            entityData.set(MONUMENT_POS, BlockPos.of(tag.getLong("MineCivMonument")));
        }
        if (tag.contains("MineCivRole")) {
            entityData.set(ROLE, tag.getString("MineCivRole"));
        }
    }

    /* ==================== AI GOALS ==================== */

    static class ReturnToCivBorderGoal extends Goal {
        private final MineCivNpcBase npc;
        private final double speed;

        ReturnToCivBorderGoal(MineCivNpcBase npc, double speed) {
            this.npc = npc;
            this.speed = speed;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return npc.distanceToSqr(Vec3.atCenterOf(npc.getMonument())) >
                    (npc.getCivRadius() * npc.getCivRadius());
        }

        @Override
        public void start() {
            npc.getNavigation().moveTo(
                    npc.getMonument().getX() + 0.5,
                    npc.getMonument().getY(),
                    npc.getMonument().getZ() + 0.5,
                    speed
            );
        }
    }

    static class RoleAwareWanderGoal extends Goal {
        private final MineCivNpcBase npc;
        private final double speed;

        RoleAwareWanderGoal(MineCivNpcBase npc, double speed) {
            this.npc = npc;
            this.speed = speed;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return npc.getNavigation().isDone();
        }

        @Override
        public void start() {
            Vec3 target = AiUtil.randomPointInRadius(npc);
            npc.getNavigation().moveTo(target.x, target.y, target.z, speed);
        }
    }
}
