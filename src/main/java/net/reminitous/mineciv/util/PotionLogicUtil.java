package net.reminitous.mineciv.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;

import java.util.ArrayList;
import java.util.List;

public final class PotionLogicUtil {

    private PotionLogicUtil() {}

    /** Returns ALL effects on this potion stack (base potion + custom effects). */
    public static List<MobEffectInstance> effectsOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return List.of();

        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) return List.of();

        // 1.21.x: iterable -> list
        List<MobEffectInstance> out = new ArrayList<>();
        for (MobEffectInstance eff : contents.getAllEffects()) {
            if (eff != null) out.add(eff);
        }
        return out;
    }

    public static boolean isHarmfulPotion(ItemStack stack) {
        return isHarmfulEffects(effectsOf(stack));
    }

    public static boolean isBeneficialPotion(ItemStack stack) {
        return isBeneficialEffects(effectsOf(stack));
    }

    public static boolean isHarmfulEffects(List<MobEffectInstance> effects) {
        if (effects == null || effects.isEmpty()) return false;

        for (MobEffectInstance eff : effects) {
            if (eff == null) continue;

            var holder = eff.getEffect();
            if (holder == null) continue;

            if (holder.value().getCategory() == MobEffectCategory.HARMFUL) {
                return true;
            }
        }
        return false;
    }

    public static boolean isBeneficialEffects(List<MobEffectInstance> effects) {
        if (effects == null || effects.isEmpty()) return false;

        for (MobEffectInstance eff : effects) {
            if (eff == null) continue;

            var holder = eff.getEffect();
            if (holder == null) continue;

            if (holder.value().getCategory() == MobEffectCategory.BENEFICIAL) {
                return true;
            }
        }
        return false;
    }

    /** Combine two effect lists (handy for clouds). */
    public static List<MobEffectInstance> combinedEffects(List<MobEffectInstance> base, List<MobEffectInstance> custom) {
        List<MobEffectInstance> out = new ArrayList<>();
        if (base != null && !base.isEmpty()) out.addAll(base);
        if (custom != null && !custom.isEmpty()) out.addAll(custom);
        return out;
    }
}
