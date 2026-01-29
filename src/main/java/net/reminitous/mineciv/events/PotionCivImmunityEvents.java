package net.reminitous.mineciv.events;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.util.CivLookupUtil;
import net.reminitous.mineciv.util.PotionLogicUtil;

import java.util.List;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PotionCivImmunityEvents {

    private PotionCivImmunityEvents() {}

    /**
     * Replaces vanilla splash potion application with civ-aware application:
     * - Harmful potion: do NOT affect same-civ allies (immunity)
     * - Beneficial potion: do NOT buff non-civ enemies
     *
     * This runs for ALL thrown splash potions (including player-thrown), but only filters
     * when CivLookupUtil can compare civs.
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent e) {
        if (!(e.getEntity() instanceof ThrownPotion thrown)) return;
        if (!(thrown.level() instanceof ServerLevel level)) return;

        Entity owner = thrown.getOwner();
        if (owner == null) return;

// Only customize MineCiv NPC potions (prevents weird interactions with player potions / other mods)
        if (!(owner instanceof net.reminitous.mineciv.npc.MineCivNpcBase)) return;


        MinecraftServer server = level.getServer();
        if (server == null) return;

        // Potion item used to derive effects (base + custom)
        ItemStack potionStack = thrown.getItem();
        List<MobEffectInstance> effects = PotionLogicUtil.effectsOf(potionStack);
        if (effects.isEmpty()) return;

        boolean harmful = PotionLogicUtil.isHarmfulEffects(effects);
        boolean beneficial = PotionLogicUtil.isBeneficialEffects(effects);

        // If it’s neither clearly harmful nor beneficial (e.g. water bottles), leave vanilla alone.
        if (!harmful && !beneficial) return;

        // Cancel vanilla potion splash processing
        e.setCanceled(true);

        // Vanilla splash radius is roughly 4 blocks. We'll use 4 here.
        Vec3 center = thrown.position();
        double radius = 4.0D;

        AABB box = new AABB(
                center.x - radius, center.y - 2.0D, center.z - radius,
                center.x + radius, center.y + 2.0D, center.z + radius
        );

        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, box, le -> le.isAlive());
        for (LivingEntity victim : candidates) {
            if (victim == null || victim.isRemoved()) continue;

            // Distance-based intensity (close = strong, far = weak)
            double dist = victim.position().distanceTo(center);
            if (dist > radius) continue;

            float intensity = (float) (1.0D - (dist / radius));
            if (intensity <= 0.0F) continue;

            boolean sameCiv = CivLookupUtil.isSameCiv(owner, victim, server);

            // Civ filtering rules
            if (harmful && sameCiv) continue;         // protect allies from harm
            if (beneficial && !sameCiv) continue;     // prevent buffing enemies

            applyScaledEffects(owner, victim, effects, intensity);
        }

        // Remove potion entity after "our" splash
        thrown.discard();
    }

    private static void applyScaledEffects(Entity owner, LivingEntity victim, List<MobEffectInstance> effects, float intensity) {
        for (MobEffectInstance base : effects) {
            if (base == null) continue;

            var holder = base.getEffect();
            if (holder == null) continue;

            MobEffect effect = holder.value();
            if (effect == null) continue;

            int amplifier = base.getAmplifier();

            // Scale duration with intensity (like vanilla splash)
            int duration = Math.round(base.getDuration() * intensity);
            if (duration <= 0) continue;

            // Instant effects: approximate by applying a very short instance.
            // (This keeps compilation simple & behavior acceptable. We can refine later.)
            if (effect.isInstantenous()) {
                duration = 1;
            }

            MobEffectInstance applied = new MobEffectInstance(
                    holder,
                    duration,
                    amplifier,
                    base.isAmbient(),
                    base.isVisible(),
                    base.showIcon()
            );

            // addEffect(instance, source) exists on LivingEntity in modern MC
            victim.addEffect(applied, owner);
        }
    }
}
