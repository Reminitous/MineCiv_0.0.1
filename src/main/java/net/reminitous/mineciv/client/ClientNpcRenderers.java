package net.reminitous.mineciv.client;

import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.npc.MineCivArcherNpc;
import net.reminitous.mineciv.npc.MineCivFarmerNpc;
import net.reminitous.mineciv.npc.MineCivKnightNpc;
import net.reminitous.mineciv.registry.ModEntities;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientNpcRenderers {

    private ClientNpcRenderers() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers e) {

        e.registerEntityRenderer(ModEntities.NPC_ARCHER.get(), ctx ->
                new RoleRenderer<>(ctx, texture("entity/npc/archer.png"))
        );

        e.registerEntityRenderer(ModEntities.NPC_KNIGHT.get(), ctx ->
                new RoleRenderer<>(ctx, texture("entity/npc/knight.png"))
        );

        e.registerEntityRenderer(ModEntities.NPC_FARMER.get(), ctx ->
                new RoleRenderer<>(ctx, texture("entity/npc/farmer.png"))
        );
    }

    private static ResourceLocation texture(String path) {
        return ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/" + path);
    }

    private static final class RoleRenderer<T extends net.minecraft.world.entity.Mob>
            extends HumanoidMobRenderer<T, ZombieModel<T>> {

        private final ResourceLocation tex;

        RoleRenderer(EntityRendererProvider.Context ctx, ResourceLocation tex) {
            super(ctx, new ZombieModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE)), 0.5F);
            this.tex = tex;
        }

        @Override
        public ResourceLocation getTextureLocation(T entity) {
            return tex;
        }
    }
}
