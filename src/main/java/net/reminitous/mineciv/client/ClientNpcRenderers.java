package net.reminitous.mineciv.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.registry.ModEntities;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientNpcRenderers {

    private ClientNpcRenderers() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ModEntities.NPC_ARCHER.get(), ctx -> new MineCivVillagerNpcRenderer(ctx));
        e.registerEntityRenderer(ModEntities.NPC_KNIGHT.get(), ctx -> new MineCivVillagerNpcRenderer(ctx));
        e.registerEntityRenderer(ModEntities.NPC_FARMER.get(), ctx -> new MineCivVillagerNpcRenderer(ctx));
        // add the rest as you register them in ModEntities
    }
}
