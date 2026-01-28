package net.reminitous.mineciv.npc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import net.reminitous.mineciv.npc.ai.StayNearMonumentGoal;
import net.reminitous.mineciv.npc.ai.WitchBrewGoal;

public class MineCivWitchNpc extends MineCivNpcBase {

    public MineCivWitchNpc(EntityType<? extends MineCivWitchNpc> type, Level level) {
        super(type, level);
        setRole("WITCH");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MineCivNpcBase.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.50D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();

        this.goalSelector.addGoal(3, new StayNearMonumentGoal(this, 0.95D, 28, 30));
        this.goalSelector.addGoal(2, new WitchBrewGoal(this, 0.95D, 24));
    }
}
