package net.reminitous.mineciv.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.npc.CivNpcSpawnManager;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class CivNpcTickEvents {

    private CivNpcTickEvents() {}

    // every 10 seconds
    private static final int SCAN_EVERY_TICKS = 20 * 10;
    private static int tickCounter = 0;

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < SCAN_EVERY_TICKS) return;
        tickCounter = 0;

        ServerLevel overworld = e.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        CivNpcSpawnManager.maintainAllCivs(overworld);
    }
}
