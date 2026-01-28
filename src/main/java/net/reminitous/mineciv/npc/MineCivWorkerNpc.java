package net.reminitous.mineciv.npc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

import net.reminitous.mineciv.npc.ai.StayNearMonumentGoal;

public class MineCivWorkerNpc extends MineCivNpcBase {

    public MineCivWorkerNpc(EntityType<? extends MineCivWorkerNpc> type, Level level) {
        super(type, level);
        setRole("WORKER");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MineCivNpcBase.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 24.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.52D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(3, new StayNearMonumentGoal(this, 0.95D, 28, 30));
    }
}
