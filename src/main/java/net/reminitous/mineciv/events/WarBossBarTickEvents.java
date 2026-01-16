package net.reminitous.mineciv.events;

import net.minecraft.server.level.ServerLevel;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.war.WarBossBarManager;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class WarBossBarTickEvents {

    private WarBossBarTickEvents() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        // any level works to get server, but event doesn't provide server directly
        // so we do nothing here; Forge 1.21 still lets us use the static server instance via level events,
        // BUT the simplest stable way is: iterate all worlds through ServerLifecycleHooks
        // We’ll use that instead to avoid nulls.
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;

        WarBossBarManager.tick(server);
    }
}
