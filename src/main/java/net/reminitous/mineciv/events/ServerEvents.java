package net.reminitous.mineciv.events;

import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.commands.MineCivCommands;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class ServerEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        MineCivCommands.register(event.getDispatcher());
    }
}
