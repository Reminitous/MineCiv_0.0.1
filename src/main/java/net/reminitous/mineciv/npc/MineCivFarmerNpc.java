package net.reminitous.mineciv.npc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import net.reminitous.mineciv.npc.ai.StayNearMonumentGoal;
import net.reminitous.mineciv.npc.ai.FarmerWorkGoal;

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

        // Stay around monument (town center)
        this.goalSelector.addGoal(3, new StayNearMonumentGoal(this, 0.85D, 24, 40));

        // Actual farming work (higher priority than wandering)
        this.goalSelector.addGoal(2, new FarmerWorkGoal(this, 0.95D, 12, 16));
    }
}
