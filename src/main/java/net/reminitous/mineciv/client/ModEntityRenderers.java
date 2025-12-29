package net.reminitous.mineciv.client;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.entity.ModEntities;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = net.minecraftforge.api.distmarker.Dist.CLIENT)
public class ModEntityRenderers {

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.KNIGHT.get(), net.reminitous.mineciv.client.render.KnightRenderer::new);
    }
}
