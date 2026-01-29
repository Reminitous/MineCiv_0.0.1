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

        // Agricultural
        e.registerEntityRenderer(ModEntities.NPC_FARMER.get(),     MineCivPlayerNpcRenderer::new);
        e.registerEntityRenderer(ModEntities.NPC_SHEPHERD.get(),   MineCivPlayerNpcRenderer::new);
        e.registerEntityRenderer(ModEntities.NPC_LUMBERJACK.get(), MineCivPlayerNpcRenderer::new);

        // Warlike
        e.registerEntityRenderer(ModEntities.NPC_PATROL.get(),     MineCivPlayerNpcRenderer::new);
        e.registerEntityRenderer(ModEntities.NPC_KNIGHT.get(),     MineCivPlayerNpcRenderer::new);
        e.registerEntityRenderer(ModEntities.NPC_ARCHER.get(),     MineCivPlayerNpcRenderer::new);

        // Technology
        e.registerEntityRenderer(ModEntities.NPC_WORKER.get(),     MineCivPlayerNpcRenderer::new);
        e.registerEntityRenderer(ModEntities.NPC_MINER.get(),      MineCivPlayerNpcRenderer::new);

        // Mystic
        e.registerEntityRenderer(ModEntities.NPC_WITCH.get(),      MineCivPlayerNpcRenderer::new);
        e.registerEntityRenderer(ModEntities.NPC_WIZARD.get(),     MineCivPlayerNpcRenderer::new);
        e.registerEntityRenderer(ModEntities.NPC_ENCHANTER.get(),  MineCivPlayerNpcRenderer::new);
    }
}
