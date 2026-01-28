package net.reminitous.mineciv.npc;

import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

import javax.annotation.Nullable;

public final class MineCivArcherNpc extends MineCivNpcBase {

    public MineCivArcherNpc(EntityType<? extends MineCivArcherNpc> type, Level level) {
        super(type, level);
        setRole("archer");
    }

    public static AttributeSupplier.Builder createAttributes() {
        return createLivingAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.5D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    @Nullable
    public net.minecraft.world.entity.SpawnGroupData finalizeSpawn(
            ServerLevelAccessor level,
            DifficultyInstance difficulty,
            MobSpawnType reason,
            @Nullable net.minecraft.world.entity.SpawnGroupData spawnData) {
        var data = super.finalizeSpawn(level, difficulty, reason, spawnData);

        this.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BOW));
        this.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.ARROW));

        return data;
    }
}
