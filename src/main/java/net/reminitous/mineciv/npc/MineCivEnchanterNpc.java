package net.reminitous.mineciv.npc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import net.reminitous.mineciv.npc.ai.EnchanterGenerateGoal;
import net.reminitous.mineciv.npc.ai.StayNearMonumentGoal;

public class MineCivEnchanterNpc extends MineCivNpcBase {

    public MineCivEnchanterNpc(EntityType<? extends MineCivEnchanterNpc> type, Level level) {
        super(type, level);
        setRole("ENCHANTER");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MineCivNpcBase.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.50D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(3, new StayNearMonumentGoal(this, 0.95D, 26, 30));
        this.goalSelector.addGoal(2, new EnchanterGenerateGoal(this, 0.95D, 24));
    }
}
