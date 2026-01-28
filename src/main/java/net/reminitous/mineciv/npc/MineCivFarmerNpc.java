package net.reminitous.mineciv.npc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import net.reminitous.mineciv.npc.ai.StayNearMonumentGoal;

public class MineCivFarmerNpc extends MineCivNpcBase {

    public MineCivFarmerNpc(EntityType<? extends MineCivFarmerNpc> type, Level level) {
        super(type, level);
        setRole("FARMER");
    }

    public static AttributeSupplier.Builder createAttributes() {
        // Use PathfinderMob/Mob attributes instead of Villager attributes
        return MineCivNpcBase.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 22.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.50D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(3, new StayNearMonumentGoal(this, 0.85D, 24, 40));
    }
}
