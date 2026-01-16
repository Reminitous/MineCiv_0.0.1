package net.reminitous.mineciv.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ClientScreens {

    private ClientScreens() {}

    public static void open(Screen screen) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        mc.setScreen(screen);
    }
}
