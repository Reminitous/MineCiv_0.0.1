package net.reminitous.mineciv;

import com.mojang.logging.LogUtils;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.registry.ModBlockEntities;
import net.reminitous.mineciv.registry.ModBlocks;

@Mod(MineCiv.MOD_ID)
public final class MineCiv {

    public static final String MOD_ID = "mineciv";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MineCiv() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();

        // Register content
        ModBlocks.BLOCKS.register(modBus);
        ModBlockEntities.BLOCK_ENTITIES.register(modBus);

        // Initialize networking
        Network.init();
    }
}
