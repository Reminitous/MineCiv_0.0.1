package net.reminitous.mineciv.npc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;

import net.reminitous.mineciv.npc.ai.CivAggressorTargetGoal;
import net.reminitous.mineciv.npc.ai.StayNearMonumentGoal;
import net.reminitous.mineciv.npc.ai.StayWithinTerritoryRangeGoal;
import net.reminitous.mineciv.npc.ai.WizardPotionAttackGoal;

public class MineCivWizardNpc extends MineCivNpcBase {

    public MineCivWizardNpc(EntityType<? extends MineCivWizardNpc> type, Level level) {
        super(type, level);
        setRole("WIZARD");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MineCivNpcBase.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.50D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        // Wizard can roam farther (v1: near monument + enforced 3-chunk range)
        this.goalSelector.addGoal(3, new StayNearMonumentGoal(this, 1.00D, 46, 25)); // ~3 chunks radius
        this.goalSelector.addGoal(1, new StayWithinTerritoryRangeGoal(this, 1.10D, 3)); // 3 chunks outside territory max

        // Potion throwing combat
        this.goalSelector.addGoal(2, new WizardPotionAttackGoal(this));

        // Targets:
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Monster.class, true));
        this.targetSelector.addGoal(3, new CivAggressorTargetGoal(this)); // players only if they attacked civ recently
    }
}
