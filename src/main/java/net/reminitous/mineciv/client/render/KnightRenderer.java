package net.reminitous.mineciv.client.render;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.resources.ResourceLocation;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.entity.custom.KnightNPC;

public class KnightRenderer extends HumanoidMobRenderer<KnightNPC, PlayerModel<KnightNPC>> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    MineCiv.MOD_ID,
                    "textures/entity/knight.png"
            );

    public KnightRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(KnightNPC entity) {
        return TEXTURE;
    }
}
