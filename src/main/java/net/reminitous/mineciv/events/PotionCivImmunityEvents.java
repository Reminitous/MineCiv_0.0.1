package net.reminitous.mineciv.events;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ThrownPotion;

import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.util.CivLookupUtil;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class PotionCivImmunityEvents {

    private PotionCivImmunityEvents() {}

    /**
     * Enforce civ immunity/buff rules.
     *
     * IMPORTANT:
     * - In Forge 1.21.1, MobEffectEvent.Applicable does NOT expose a "source".
     * - MobEffectEvent.Added DOES expose getEffectSource().
     *
     * Behavior:
     *  - Harmful effects: allies immune (remove effect if same civ as applier)
     *  - Beneficial effects: enemies don't receive buffs (remove effect if NOT same civ as applier)
     */
    @SubscribeEvent
    public static void onEffectAdded(MobEffectEvent.Added event) {
        LivingEntity target = event.getEntity();
        MobEffectInstance inst = event.getEffectInstance();
        if (target == null || inst == null) return;

        // Category in 1.21.x: Holder<MobEffect> -> value()
        MobEffectCategory cat = inst.getEffect().value().getCategory();
        boolean harmful = (cat == MobEffectCategory.HARMFUL);
        boolean beneficial = (cat == MobEffectCategory.BENEFICIAL);

        if (!harmful && !beneficial) return;

        // "Effect source" may be:
        // - the actual attacker,
        // - an AreaEffectCloud / ThrownPotion / Arrow,
        // - or null (commands/environment/etc).
        Entity rawSource = event.getEffectSource();
        Entity applier = unwrapApplier(rawSource);
        if (applier == null) return;

        MinecraftServer server = applier.level().getServer();
        if (server == null) return;

        boolean sameCiv = CivLookupUtil.isSameCiv(applier, target, server);

        // Harmful: protect allies (remove it)
        if (harmful && sameCiv) {
            target.removeEffect(inst.getEffect());
            return;
        }

        // Beneficial: don't buff enemies (remove it)
        if (beneficial && !sameCiv) {
            target.removeEffect(inst.getEffect());
        }
    }

    /**
     * Tries to convert "effect source" into the real applier entity.
     * For potions/clouds/arrows this is usually their owner.
     */
    private static Entity unwrapApplier(Entity source) {
        if (source == null) return null;

        // Lingering potion cloud
        if (source instanceof AreaEffectCloud cloud) {
            Entity owner = cloud.getOwner();
            return owner != null ? owner : cloud;
        }

        // Thrown splash/lingering potion entity
        if (source instanceof ThrownPotion potion) {
            Entity owner = potion.getOwner();
            return owner != null ? owner : potion;
        }

        // Tipped arrows / spectral arrows etc.
        if (source instanceof AbstractArrow arrow) {
            Entity owner = arrow.getOwner();
            return owner != null ? owner : arrow;
        }

        return source;
    }
}
