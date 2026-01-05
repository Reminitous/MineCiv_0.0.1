package net.reminitous.mineciv.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.AnimalTameEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.progress.XpValues;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class CivXpEvents {

    private CivXpEvents() {}

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getPlayer() instanceof ServerPlayer player)) return;

        BlockState state = e.getState();
        long xpMine = XpValues.forMinedBlock(state);
        long xpHarvest = XpValues.forHarvest(state);

        long xp = Math.max(xpMine, xpHarvest);
        if (xp > 0) {
            CivilizationManager.awardCivXp(level, player.getUUID(), xp);
        }
    }

    @SubscribeEvent
    public static void onMobKill(LivingDeathEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level)) return;

        Entity killer = e.getSource().getEntity();
        if (!(killer instanceof ServerPlayer player)) return;

        long xp = XpValues.forMobKill(e.getEntity().getType());
        if (xp > 0) {
            CivilizationManager.awardCivXp(level, player.getUUID(), xp);
        }
    }

    @SubscribeEvent
    public static void onBreed(BabyEntitySpawnEvent e) {
        if (!(e.getChild().level() instanceof ServerLevel level)) return;

        // Find nearest real player within reasonable range
        var nearest = level.getNearestPlayer(
                e.getChild().getX(),
                e.getChild().getY(),
                e.getChild().getZ(),
                8.0,
                false
        );

        if (!(nearest instanceof ServerPlayer player)) return;

        if (e.getChild() instanceof Animal) {
            long xp = XpValues.forBreed(e.getChild().getType());
            CivilizationManager.awardCivXp(level, player.getUUID(), xp);
        }
    }

    @SubscribeEvent
    public static void onTame(AnimalTameEvent e) {
        if (!(e.getTamer() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        long xp = XpValues.forTame(e.getAnimal().getType());
        CivilizationManager.awardCivXp(level, player.getUUID(), xp);
    }
}
