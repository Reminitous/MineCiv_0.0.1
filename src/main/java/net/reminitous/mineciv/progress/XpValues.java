package net.reminitous.mineciv.progress;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

public final class XpValues {

    private XpValues() {}

    public static long forMinedBlock(BlockState state) {
        Block b = state.getBlock();

        // Very rough initial tuning — easy to tweak later
        String key = b.toString().toLowerCase();

        if (key.contains("diamond")) return 250;
        if (key.contains("ancient_debris")) return 400;
        if (key.contains("emerald")) return 220;
        if (key.contains("gold_ore") || key.contains("deepslate_gold_ore")) return 140;
        if (key.contains("iron_ore") || key.contains("deepslate_iron_ore")) return 80;
        if (key.contains("coal_ore") || key.contains("deepslate_coal_ore")) return 35;
        if (key.contains("redstone_ore") || key.contains("deepslate_redstone_ore")) return 70;
        if (key.contains("lapis_ore") || key.contains("deepslate_lapis_ore")) return 90;
        if (key.contains("copper_ore") || key.contains("deepslate_copper_ore")) return 50;

        // Default: small XP for general mining
        return 5;
    }

    public static long forHarvest(BlockState state) {
        if (!(state.getBlock() instanceof CropBlock crop)) return 0;

        // Public API in 1.21.x
        if (!crop.isMaxAge(state)) return 0;

        return 25;
    }

    public static long forBreed(EntityType<?> type) {
        // Simple baseline; later tune per animal type
        return 40;
    }

    public static long forTame(EntityType<?> type) {
        if (type == EntityType.WOLF) return 60;
        if (type == EntityType.CAT) return 60;
        if (type == EntityType.PARROT) return 90;
        if (type == EntityType.HORSE) return 120;
        if (type == EntityType.DONKEY) return 120;
        if (type == EntityType.MULE) return 120;
        return 50;
    }

    public static long forMobKill(EntityType<?> type) {
        // Hostile mobs give more; later tune by difficulty.
        if (Monster.class.isAssignableFrom(type.getBaseClass())) return 35;
        return 10;
    }
}
