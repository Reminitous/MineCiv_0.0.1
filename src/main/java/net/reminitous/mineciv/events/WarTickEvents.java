package net.reminitous.mineciv.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.war.WarManager;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class WarTickEvents {

    private WarTickEvents() {}

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.level instanceof ServerLevel level)) return;

        // Tick once per second
        if ((level.getServer().getTickCount() % 20) != 0) return;

        WarManager.tick(level);
    }
}
