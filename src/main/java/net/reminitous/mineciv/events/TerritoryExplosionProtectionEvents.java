package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.level.Explosion;

import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.territory.TerritoryManager;

import java.util.Iterator;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class TerritoryExplosionProtectionEvents {

    private TerritoryExplosionProtectionEvents() {}

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;

        Explosion explosion = e.getExplosion();
        Entity source = explosion.getDirectSourceEntity(); // may be null

        // ✅ Allow creepers to damage blocks anywhere
        if (source instanceof Creeper) {
            return;
        }

        // ❌ Only block TNT-like explosions
        if (!isTntLike(source)) {
            return;
        }

        // Remove blocks inside claimed territory from explosion
        List<BlockPos> affected = e.getAffectedBlocks();
        if (affected.isEmpty()) return;

        Iterator<BlockPos> it = affected.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            UUID owner = TerritoryManager.getOwnerCivId(level, pos);
            if (owner != null) {
                it.remove();
            }
        }
    }

    private static boolean isTntLike(Entity src) {
        return src instanceof PrimedTnt
                || src instanceof MinecartTNT;
    }
}
