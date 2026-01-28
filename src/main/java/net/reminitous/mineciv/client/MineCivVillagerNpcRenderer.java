package net.reminitous.mineciv.client;

import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.npc.MineCivNpcBase;

public final class MineCivVillagerNpcRenderer extends MobRenderer<MineCivNpcBase, VillagerModel<MineCivNpcBase>> {

    private static final ResourceLocation TEX_DEFAULT =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/default.png");
    private static final ResourceLocation TEX_ARCHER =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/archer.png");
    private static final ResourceLocation TEX_KNIGHT =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/knight.png");
    private static final ResourceLocation TEX_FARMER =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/farmer.png");

    public MineCivVillagerNpcRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new VillagerModel<>(ctx.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(MineCivNpcBase npc) {
        String role = npc.getRole();
        if (role == null) return TEX_DEFAULT;

        return switch (role.toLowerCase()) {
            case "archer" -> TEX_ARCHER;
            case "knight" -> TEX_KNIGHT;
            case "farmer" -> TEX_FARMER;
            default -> TEX_DEFAULT;
        };
    }
}
