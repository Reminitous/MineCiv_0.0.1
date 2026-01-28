package net.reminitous.mineciv.npc.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.npc.MineCivNpcBase;

import java.util.EnumSet;
import java.util.UUID;

public class WizardPotionAttackGoal extends Goal {

    private final Mob mob;
    private int cooldown = 0;

    public WizardPotionAttackGoal(Mob mob) {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        return mob.getTarget() != null;
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (!(mob instanceof MineCivNpcBase base)) return;

        LivingEntity target = mob.getTarget();
        if (target == null) return;

        if (cooldown-- > 0) return;
        cooldown = 40; // throw every 2 seconds

        ItemStack potion = choosePotion(level, base, target);
        if (potion.isEmpty()) return;

        ThrownPotion thrown = new ThrownPotion(level, mob);
        thrown.setItem(potion);
        thrown.setPos(mob.getX(), mob.getEyeY() - 0.1D, mob.getZ());
        thrown.shootFromRotation(mob, mob.getXRot(), mob.getYRot(), -20.0F, 0.5F, 1.0F);

        mob.playSound(SoundEvents.WITCH_THROW, 1.0F, 1.0F);
        level.addFreshEntity(thrown);
    }

    /* ---------------- Potion logic ---------------- */

    private ItemStack choosePotion(ServerLevel level, MineCivNpcBase wizard, LivingEntity target) {
        // Heal allies
        if (isSameCiv(level, wizard.getCivId(), target) && target.getHealth() < target.getMaxHealth() * 0.6F) {
            return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.STRONG_HEALING);
        }

        // Debuff enemies
        if (target instanceof Monster || target instanceof Player) {
            double dist = wizard.distanceToSqr(target);

            if (dist > 64.0D) {
                return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.SLOWNESS);
            }
            if (target.getHealth() > 12.0F) {
                return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.POISON);
            }
            return PotionContents.createItemStack(Items.SPLASH_POTION, Potions.HARMING);
        }

        return ItemStack.EMPTY;
    }

    private boolean isSameCiv(ServerLevel level, UUID wizardCivId, LivingEntity entity) {
        if (wizardCivId == null) return false;

        // NPC ally check: players only (extend later if you add NPC-vs-NPC civ checks)
        if (!(entity instanceof Player player)) return false;

        CivSavedData data = CivSavedData.get(level.getServer());
        Civilization civ = data.civs().get(wizardCivId);
        if (civ == null) return false;

        // Treat leader as member too
        return player.getUUID().equals(civ.leader()) || civ.isMember(player.getUUID());
    }
}
