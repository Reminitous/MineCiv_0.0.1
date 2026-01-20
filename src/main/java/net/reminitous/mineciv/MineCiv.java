package net.reminitous.mineciv;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.registry.ModBlockEntities;
import net.reminitous.mineciv.registry.ModBlocks;
import net.reminitous.mineciv.registry.ModLootModifiers;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;


@Mod(MineCiv.MOD_ID)
public final class MineCiv {

    public static final String MOD_ID = "mineciv";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MineCiv() {
        // Forge 52.1.8: this is the correct mod event bus source
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        ModBlocks.BLOCKS.register(modBus);
        ModBlocks.ITEMS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);
        ModLootModifiers.LOOT_MODIFIERS.register(modBus);
        Network.init();

        private static void addCreative(BuildCreativeModeTabContentsEvent event) {
            if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
                event.accept(net.reminitous.mineciv.registry.ModBlocks.MONUMENT.get());
            }
        }

    }
}

