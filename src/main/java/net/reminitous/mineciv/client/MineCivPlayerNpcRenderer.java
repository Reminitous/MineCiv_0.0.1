package net.reminitous.mineciv.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.resources.ResourceLocation;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.npc.MineCivNpcBase;

public final class MineCivPlayerNpcRenderer extends HumanoidMobRenderer<MineCivNpcBase, PlayerModel<MineCivNpcBase>> {

    private static final ResourceLocation TEX_DEFAULT =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/default.png");

    // Agricultural
    private static final ResourceLocation TEX_FARMER =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/farmer.png");
    private static final ResourceLocation TEX_SHEPHERD =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/shepherd.png");
    private static final ResourceLocation TEX_LUMBERJACK =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/lumberjack.png");

    // Warlike
    private static final ResourceLocation TEX_PATROL =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/patrol.png");
    private static final ResourceLocation TEX_KNIGHT =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/knight.png");
    private static final ResourceLocation TEX_ARCHER =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/archer.png");

    // Technology
    private static final ResourceLocation TEX_WORKER =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/worker.png");
    private static final ResourceLocation TEX_MINER =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/miner.png");

    // Mystic
    private static final ResourceLocation TEX_WITCH =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/witch.png");
    private static final ResourceLocation TEX_WIZARD =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/wizard.png");
    private static final ResourceLocation TEX_ENCHANTER =
            ResourceLocation.fromNamespaceAndPath(MineCiv.MOD_ID, "textures/entity/npc/enchanter.png");

    public MineCivPlayerNpcRenderer(EntityRendererProvider.Context ctx) {
        super(
                ctx,
                // Main model (player skin layout). "false" = Steve arms; "true" = slim Alex arms.
                new PlayerModel<>(ctx.bakeLayer(ModelLayers.PLAYER), false),
                0.5F
        );

        // Armor layers: use HumanoidModel with the armor layers (most compatible with 1.21.x)
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(ctx.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                ctx.getModelManager()
        ));
    }

    @Override
    public ResourceLocation getTextureLocation(MineCivNpcBase npc) {
        String role = npc.getRole();
        if (role == null || role.isBlank()) return TEX_DEFAULT;

        return switch (role.toLowerCase()) {
            // Agricultural
            case "farmer" -> TEX_FARMER;
            case "shepherd" -> TEX_SHEPHERD;
            case "lumberjack" -> TEX_LUMBERJACK;

            // Warlike
            case "patrol" -> TEX_PATROL;
            case "knight" -> TEX_KNIGHT;
            case "archer" -> TEX_ARCHER;

            // Technology
            case "worker" -> TEX_WORKER;
            case "miner" -> TEX_MINER;

            // Mystic
            case "witch" -> TEX_WITCH;
            case "wizard" -> TEX_WIZARD;
            case "enchanter" -> TEX_ENCHANTER;

            default -> TEX_DEFAULT;
        };
    }
}
