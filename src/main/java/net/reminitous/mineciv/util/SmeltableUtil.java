package net.reminitous.mineciv.util;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;

public final class SmeltableUtil {

    private SmeltableUtil() {}

    public static boolean isSmeltableInFurnace(ServerLevel level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        return level.getRecipeManager()
                .getRecipeFor(RecipeType.SMELTING, new SingleRecipeInput(stack), level)
                .isPresent();
    }

    public static boolean isSmeltableInBlastFurnace(ServerLevel level, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;

        return level.getRecipeManager()
                .getRecipeFor(RecipeType.BLASTING, new SingleRecipeInput(stack), level)
                .isPresent();
    }
}
