package net.reminitous.mineciv.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.npc.CivNpcManager;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class CivNpcTickEvents {

    private CivNpcTickEvents() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.level instanceof ServerLevel level)) return;

        // Every 5 seconds
        if ((level.getServer().getTickCount() % 100) != 0) return;

        CivNpcManager.tick(level);
    }
}
