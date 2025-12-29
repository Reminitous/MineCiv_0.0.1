package net.reminitous.mineciv.entity.custom;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.reminitous.mineciv.block.ChunkClaimManager;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.UUID;

public class KnightNPC extends PathfinderMob {

    // ---------- NBT ----------
    private static final String NBT_OWNER = "Owner";
    private static final String NBT_MODE  = "Mode";
    private static final String NBT_POST  = "Post";

    private enum Mode { FOLLOW_OWNER, GUARD_POST }

    @Nullable private UUID owner;
    private Mode mode = Mode.GUARD_POST;
    @Nullable private BlockPos postPos;

    public KnightNPC(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);
        this.setCanPickUpLoot(false);
    }

    // ---------- ATTRIBUTES ----------
    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.30)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.ARMOR, 8.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.15);
    }

    // ---------- OWNER / MODE ----------
    public void hire(Player player) {
        this.owner = player.getUUID();
        this.mode = Mode.FOLLOW_OWNER;
        ensurePostFromCurrentClaim();
        this.setTarget(null);
    }

    public void dismissToPost() {
        this.mode = Mode.GUARD_POST;
        ensurePostFromCurrentClaim();
        this.setTarget(null);
    }

    public boolean isHired() {
        return owner != null && mode == Mode.FOLLOW_OWNER;
    }

    @Nullable
    public UUID getOwnerUUID() {
        return owner;
    }

    @Nullable
    public LivingEntity getOwnerEntity() {
        if (owner == null) return null;
        if (!(level() instanceof ServerLevel sl)) return null;
        Entity e = sl.getEntity(owner);
        return (e instanceof LivingEntity le) ? le : null;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (owner != null && other != null && other.getUUID().equals(owner)) return true;
        return super.isAlliedTo(other);
    }

    private void ensurePostFromCurrentClaim() {
        if (!(level() instanceof ServerLevel)) return;

        BlockPos here = blockPosition();
        int cx = here.getX() >> 4;
        int cz = here.getZ() >> 4;

        ChunkClaimManager.ClaimData claim = ChunkClaimManager.getClaim(level(), cx, cz);
        if (claim != null) {
            postPos = claim.monumentPos.immutable();
        } else if (postPos == null) {
            postPos = here.immutable();
        }
    }

    @Nullable
    public BlockPos getPostPos() {
        return postPos;
    }

    // ---------- AI ----------
    @Override
    protected void registerGoals() {
        // Guard-post behaviors
        this.goalSelector.addGoal(1, new KnightReturnToPostGoal(this, 1.1, 18.0));
        this.goalSelector.addGoal(6, new KnightPatrolPostGoal(this, 0.8, 10));

        // Follow owner (only when hired)
        this.goalSelector.addGoal(2, new KnightFollowOwnerGoal(this, 1.1, 3.0F, 12.0F));

        // Fight
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2, true));

        // Idle
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        // Targeting:
        // Always attack hostiles
        this.targetSelector.addGoal(1, new NearestAttackableTargetGoal<>(this, Monster.class, true));

        // Only when hired: defend owner and assist owner
        this.targetSelector.addGoal(2, new KnightDefendOwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new KnightDefendOwnerHurtTargetGoal(this));
    }

    // ---------- INTERACTION ----------
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (level().isClientSide) return InteractionResult.SUCCESS;

        ItemStack held = player.getItemInHand(hand);

        // Owner-only interaction once owned
        if (owner != null && !owner.equals(player.getUUID())) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("This Knight belongs to someone else."));
            return InteractionResult.CONSUME;
        }

        // Equip sword
        if (!held.isEmpty() && held.getItem() instanceof SwordItem) {
            equipItem(player, held, EquipmentSlot.MAINHAND);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Knight equipped with sword."));
            return InteractionResult.CONSUME;
        }

        // Equip armor
        if (!held.isEmpty() && held.getItem() instanceof ArmorItem armor) {
            equipItem(player, held, armor.getEquipmentSlot());
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Knight equipped armor."));
            return InteractionResult.CONSUME;
        }

        // Hire/dismiss toggle (shift-right-click)
        if (player.isShiftKeyDown()) {
            if (owner == null) {
                hire(player);
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Knight hired (follow mode)."));
            } else if (mode == Mode.FOLLOW_OWNER) {
                dismissToPost();
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Knight dismissed (guard post mode)."));
            } else {
                mode = Mode.FOLLOW_OWNER;
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("Knight rehired (follow mode)."));
            }
            return InteractionResult.CONSUME;
        }

        return super.mobInteract(player, hand);
    }

    private void equipItem(Player player, ItemStack held, EquipmentSlot slot) {
        ItemStack old = getItemBySlot(slot);

        setItemSlot(slot, held.copyWithCount(1));
        setDropChance(slot, 0.0F);
        setPersistenceRequired();

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        if (!old.isEmpty()) {
            player.getInventory().placeItemBackInInventory(old);
        }
    }

    // ---------- SAVE ----------
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        if (owner != null) tag.putUUID(NBT_OWNER, owner);
        tag.putString(NBT_MODE, mode.name());
        if (postPos != null) tag.putLong(NBT_POST, postPos.asLong());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        owner = tag.hasUUID(NBT_OWNER) ? tag.getUUID(NBT_OWNER) : null;

        if (tag.contains(NBT_MODE)) {
            try {
                mode = Mode.valueOf(tag.getString(NBT_MODE));
            } catch (IllegalArgumentException ignored) {
                mode = Mode.GUARD_POST;
            }
        } else {
            mode = Mode.GUARD_POST;
        }

        postPos = tag.contains(NBT_POST) ? BlockPos.of(tag.getLong(NBT_POST)) : null;
        ensurePostFromCurrentClaim();
    }

    // ==========================================================
    // GOALS (CUSTOM, NESTED) — no collision with vanilla classes
    // ==========================================================

    /** Follow owner (only when hired). */
    public static class KnightFollowOwnerGoal extends Goal {
        private final KnightNPC knight;
        private final double speed;
        private final float stopDist;
        private final float startDist;

        public KnightFollowOwnerGoal(KnightNPC knight, double speed, float stopDist, float startDist) {
            this.knight = knight;
            this.speed = speed;
            this.stopDist = stopDist;
            this.startDist = startDist;
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (!knight.isHired()) return false;
            LivingEntity owner = knight.getOwnerEntity();
            if (owner == null) return false;
            if (knight.getTarget() != null) return false;
            return knight.distanceTo(owner) > startDist;
        }

        @Override
        public boolean canContinueToUse() {
            if (!knight.isHired()) return false;
            LivingEntity owner = knight.getOwnerEntity();
            if (owner == null) return false;
            if (knight.getTarget() != null) return false;
            return knight.distanceTo(owner) > stopDist;
        }

        @Override
        public void tick() {
            LivingEntity owner = knight.getOwnerEntity();
            if (owner == null) return;

            knight.getNavigation().moveTo(owner, speed);
            knight.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        }
    }

    /** If guarding post and too far, return. */
    public static class KnightReturnToPostGoal extends Goal {
        private final KnightNPC knight;
        private final double speed;
        private final double maxDist;

        public KnightReturnToPostGoal(KnightNPC knight, double speed, double maxDist) {
            this.knight = knight;
            this.speed = speed;
            this.maxDist = maxDist;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (knight.mode != Mode.GUARD_POST) return false;
            if (knight.getTarget() != null) return false;

            BlockPos post = knight.getPostPos();
            if (post == null) {
                knight.ensurePostFromCurrentClaim();
                post = knight.getPostPos();
            }
            if (post == null) return false;

            return knight.blockPosition().distSqr(post) > (maxDist * maxDist);
        }

        @Override
        public void start() {
            BlockPos post = knight.getPostPos();
            if (post == null) return;

            knight.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, speed);
        }
    }

    /** Wander around the post while guarding. */
    public static class KnightPatrolPostGoal extends Goal {
        private final KnightNPC knight;
        private final double speed;
        private final int radius;
        private int cooldown = 0;

        public KnightPatrolPostGoal(KnightNPC knight, double speed, int radius) {
            this.knight = knight;
            this.speed = speed;
            this.radius = radius;
            this.setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            if (knight.mode != Mode.GUARD_POST) return false;
            if (knight.getTarget() != null) return false;
            if (cooldown-- > 0) return false;

            return knight.getPostPos() != null;
        }

        @Override
        public void start() {
            BlockPos post = knight.getPostPos();
            if (post == null) return;

            BlockPos dest = post.offset(
                    knight.getRandom().nextInt(radius * 2 + 1) - radius,
                    0,
                    knight.getRandom().nextInt(radius * 2 + 1) - radius
            );

            knight.getNavigation().moveTo(dest.getX() + 0.5, dest.getY(), dest.getZ() + 0.5, speed);
            cooldown = 60;
        }
    }

    /** When hired: attack whoever hurt the owner. */
    public static class KnightDefendOwnerHurtByTargetGoal extends TargetGoal {
        private final KnightNPC knight;
        private int lastTimestamp;

        public KnightDefendOwnerHurtByTargetGoal(KnightNPC knight) {
            super(knight, false);
            this.knight = knight;
        }

        @Override
        public boolean canUse() {
            if (!knight.isHired()) return false;

            LivingEntity owner = knight.getOwnerEntity();
            if (owner == null) return false;

            LivingEntity attacker = owner.getLastHurtByMob();
            int ts = owner.getLastHurtByMobTimestamp();

            if (attacker == null || ts == lastTimestamp) return false;
            if (knight.isAlliedTo(attacker)) return false;

            // Don’t target villagers
            if (attacker instanceof AbstractVillager) return false;

            // Retaliate vs players only if PvP allowed
            if (attacker instanceof Player && knight.level().getServer() != null && !knight.level().getServer().isPvpAllowed()) {
                return false;
            }

            return true;
        }

        @Override
        public void start() {
            LivingEntity owner = knight.getOwnerEntity();
            if (owner == null) return;

            LivingEntity attacker = owner.getLastHurtByMob();
            knight.setTarget(attacker);

            lastTimestamp = owner.getLastHurtByMobTimestamp();
            super.start();
        }
    }

    /** When hired: attack what the owner attacked (assist). */
    public static class KnightDefendOwnerHurtTargetGoal extends TargetGoal {
        private final KnightNPC knight;
        private int lastTimestamp;

        public KnightDefendOwnerHurtTargetGoal(KnightNPC knight) {
            super(knight, false);
            this.knight = knight;
        }

        @Override
        public boolean canUse() {
            if (!knight.isHired()) return false;

            LivingEntity owner = knight.getOwnerEntity();
            if (owner == null) return false;

            LivingEntity target = owner.getLastHurtMob();
            int ts = owner.getLastHurtMobTimestamp();

            if (target == null || ts == lastTimestamp) return false;
            if (knight.isAlliedTo(target)) return false;

            if (target instanceof AbstractVillager) return false;

            if (target instanceof Player && knight.level().getServer() != null && !knight.level().getServer().isPvpAllowed()) {
                return false;
            }

            return true;
        }

        @Override
        public void start() {
            LivingEntity owner = knight.getOwnerEntity();
            if (owner == null) return;

            LivingEntity target = owner.getLastHurtMob();
            knight.setTarget(target);

            lastTimestamp = owner.getLastHurtMobTimestamp();
            super.start();
        }
    }
}
