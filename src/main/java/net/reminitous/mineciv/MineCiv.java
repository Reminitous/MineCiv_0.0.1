package net.reminitous.mineciv;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.registry.ModBlockEntities;
import net.reminitous.mineciv.registry.ModBlocks;
import net.reminitous.mineciv.registry.ModEntities;
import net.reminitous.mineciv.registry.ModLootModifiers;

@Mod(MineCiv.MOD_ID)
public final class MineCiv {

    public static final String MOD_ID = "mineciv";
    public static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public MineCiv() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);

        // If you have entities for NPC roles:
        ModEntities.ENTITY_TYPES.register(modBus);

        // If using loot modifiers:
        ModLootModifiers.LOOT_MODIFIERS.register(modBus);

        Network.init();

        // Creative tab hook on Forge event bus
        modBus.addListener(MineCiv::addCreative);
    }

    private static void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.MONUMENT_ITEM.get());
        }
    }
}
