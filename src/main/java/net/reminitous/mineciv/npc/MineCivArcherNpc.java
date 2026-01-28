package net.reminitous.mineciv.npc;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import net.reminitous.mineciv.npc.ai.CivAggressorTargetGoal;
import net.reminitous.mineciv.npc.ai.StayNearMonumentGoal;

public class MineCivArcherNpc extends MineCivNpcBase implements RangedAttackMob {

    public MineCivArcherNpc(EntityType<? extends MineCivArcherNpc> type, Level level) {
        super(type, level);
        setRole("ARCHER");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MineCivNpcBase.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.52D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Patrol/guard near monument
        this.goalSelector.addGoal(2, new StayNearMonumentGoal(this, 0.90D, 28, 30));

        // Combat goals
        this.goalSelector.addGoal(4, new RangedBowAttackGoal<>(this, 1.0D, 20, 16.0F));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
        this.targetSelector.addGoal(3, new CivAggressorTargetGoal(this)); // players who attacked civ recently
    }

    @Override
    public SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            MobSpawnType reason,
            SpawnGroupData spawnData
    ) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData);

        // Equip bow + some arrows
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
        this.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.ARROW, 32));

        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);
        this.setDropChance(EquipmentSlot.OFFHAND, 0.0F);

        return data;
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        // 1.21.x safe: create via EntityType
        Arrow arrow = EntityType.ARROW.create(this.level());
        if (arrow == null) return;

        arrow.setOwner(this);
        arrow.setPos(this.getX(), this.getEyeY() - 0.1D, this.getZ());

        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double dy = target.getY(0.3333333333333333D) - arrow.getY();
        double dist = Math.sqrt(dx * dx + dz * dz);

        arrow.shoot(dx, dy + dist * 0.2D, dz, 1.6F, 10.0F);
        arrow.setBaseDamage(3.0D);
        arrow.setCritArrow(this.random.nextFloat() < 0.25F);

        this.playSound(
                SoundEvents.SKELETON_SHOOT,
                1.0F,
                1.0F / (this.random.nextFloat() * 0.4F + 0.8F)
        );

        this.level().addFreshEntity(arrow);

        // Consume “ammo” logically
        ItemStack off = this.getItemInHand(InteractionHand.OFF_HAND);
        if (off.is(Items.ARROW) && off.getCount() > 0) {
            off.shrink(1);
        }
    }
}
