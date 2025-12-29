package net.reminitous.mineciv;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.reminitous.mineciv.block.ChunkClaimManager;
import net.reminitous.mineciv.block.ModBlocks;
import net.reminitous.mineciv.item.ModCreativeModeTabs;
import net.reminitous.mineciv.item.ModItems;
import net.reminitous.mineciv.screen.MonumentMenu;
import net.reminitous.mineciv.villager.ModVillagers;
import org.slf4j.Logger;
import net.reminitous.mineciv.block.entity.ModBlockEntities;
import net.reminitous.mineciv.entity.ModEntities;

@Mod(MineCiv.MOD_ID)
public class MineCiv {
    public static final String MOD_ID = "mineciv";
    public static final Logger LOGGER = LogUtils.getLogger();

    public MineCiv(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        MinecraftForge.EVENT_BUS.register(this);

        ModCreativeModeTabs.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        MonumentMenu.register(modEventBus);
        ModVillagers.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModEntities.register(modEventBus);

        modEventBus.addListener(this::addCreative);
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if(event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
            event.accept(ModItems.KNIGHT_SPAWN_EGG);
        }

        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(ModBlocks.MONUMENT_BLOCK);
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {

    }

    @SubscribeEvent
    public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (event.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
            int chunkX = event.getPos().getX() >> 4;
            int chunkZ = event.getPos().getZ() >> 4;

            // Allow monument placement even in claimed chunks (the MonumentBlock handles validation)
            if (event.getPlacedBlock().getBlock() == ModBlocks.MONUMENT_BLOCK.get()) {
                return;
            }

            if (!ChunkClaimManager.canPlayerEdit(event.getLevel(), chunkX, chunkZ, player.getUUID())) {
                event.setCanceled(true);
                player.sendSystemMessage(Component.literal("You cannot place blocks in this claimed chunk!"));
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getLevel().isClientSide()) return;

        int chunkX = event.getPos().getX() >> 4;
        int chunkZ = event.getPos().getZ() >> 4;

        if (!ChunkClaimManager.canPlayerEdit(event.getLevel(), chunkX, chunkZ, event.getPlayer().getUUID())) {
            event.setCanceled(true);
            event.getPlayer().sendSystemMessage(Component.literal("You cannot break blocks in this claimed chunk!"));
        }
    }
}