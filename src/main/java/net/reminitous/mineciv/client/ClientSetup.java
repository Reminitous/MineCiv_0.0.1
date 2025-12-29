package net.reminitous.mineciv.client;

import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.screen.MonumentMenu;
import net.reminitous.mineciv.screen.MonumentScreen;

@Mod.EventBusSubscriber(
        modid = MineCiv.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT
)
public class ClientSetup {

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MenuScreens.register(
                    MonumentMenu.MONUMENT_MENU.get(),
                    MonumentScreen::new
            );
        });
    }
}
