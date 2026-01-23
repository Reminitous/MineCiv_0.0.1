package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.registry.ModBlocks;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class MonumentBreakDisbandEvents {

    private MonumentBreakDisbandEvents() {}

    // 15 minutes = 15 * 60 * 20 ticks
    private static final long REQUIRED_TICKS = 15L * 60L * 20L;

    private static final String NBT_POS = "MineCiv_MonBreakPos";
    private static final String NBT_START = "MineCiv_MonBreakStartTick";

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        if (e.getLevel().getBlockState(e.getPos()).getBlock() != ModBlocks.MONUMENT.get()) return;

        long posLong = e.getPos().asLong();
        long nowTick = level.getServer().getTickCount();

        long storedPos = player.getPersistentData().getLong(NBT_POS);
        long storedStart = player.getPersistentData().getLong(NBT_START);

        // If new monument target or no start yet, start timer now
        if (storedPos != posLong || storedStart <= 0L) {
            player.getPersistentData().putLong(NBT_POS, posLong);
            player.getPersistentData().putLong(NBT_START, nowTick);

            player.sendSystemMessage(Component.literal("⛏ Breaking a Monument takes 15 minutes in survival."));
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getPlayer() instanceof ServerPlayer player)) return;

        if (e.getState().getBlock() != ModBlocks.MONUMENT.get()) return;

        BlockPos pos = e.getPos();

        // Creative: instantly disband, no timer
        if (player.isCreative()) {
            e.setCanceled(true);
            CivilizationManager.disbandCivFromMonumentBreak(level, pos);
            return;
        }

        // Survival: enforce 15-minute timer per-player
        long posLong = pos.asLong();
        long startPos = player.getPersistentData().getLong(NBT_POS);
        long startTick = player.getPersistentData().getLong(NBT_START);

        long nowTick = level.getServer().getTickCount();

        // If they never “started” on this block, start now and cancel
        if (startPos != posLong || startTick <= 0L) {
            player.getPersistentData().putLong(NBT_POS, posLong);
            player.getPersistentData().putLong(NBT_START, nowTick);

            e.setCanceled(true);
            player.sendSystemMessage(Component.literal("⛏ Breaking a Monument takes 15 minutes in survival."));
            return;
        }

        long elapsed = nowTick - startTick;

        if (elapsed < REQUIRED_TICKS) {
            long remaining = REQUIRED_TICKS - elapsed;
            long seconds = remaining / 20L;
            long minutes = seconds / 60L;
            long sec = seconds % 60L;

            e.setCanceled(true);
            player.displayClientMessage(
                    Component.literal("Monument break: " + minutes + "m " + sec + "s remaining"),
                    true
            );
            return;
        }

        // Timer complete: disband + remove block ourselves
        e.setCanceled(true);
        boolean ok = CivilizationManager.disbandCivFromMonumentBreak(level, pos);

        if (ok) {
            player.sendSystemMessage(Component.literal("Monument destroyed. Civilization disbanded."));
        } else {
            // fallback: if disband fails for some reason, allow vanilla break
            // (but still very rare; usually ok)
            // If you want strict behavior, delete this and keep canceled.
            // e.setCanceled(false);
            player.sendSystemMessage(Component.literal("Failed to disband from monument break (unexpected)."));
        }

        // clear timer so next monument is fresh
        player.getPersistentData().remove(NBT_POS);
        player.getPersistentData().remove(NBT_START);
    }
}
