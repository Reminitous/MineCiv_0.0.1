package net.reminitous.mineciv.npc;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import net.reminitous.mineciv.npc.ai.StayNearMonumentGoal;

public class MineCivMinerNpc extends MineCivNpcBase {

    public MineCivMinerNpc(EntityType<? extends MineCivMinerNpc> type, Level level) {
        super(type, level);
        setRole("MINER");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return MineCivNpcBase.createBaseAttributes()
                .add(Attributes.MAX_HEALTH, 26.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.48D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        this.goalSelector.addGoal(3, new StayNearMonumentGoal(this, 0.90D, 30, 30));
    }

    @Override
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            net.minecraft.world.DifficultyInstance difficulty,
            MobSpawnType reason,
            net.minecraft.world.entity.SpawnGroupData spawnData
    ) {
        var data = super.finalizeSpawn(level, difficulty, reason, spawnData);

        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
        this.setDropChance(EquipmentSlot.MAINHAND, 0.0F);

        return data;
    }
}
