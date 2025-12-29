package net.reminitous.mineciv.item;

import com.mojang.brigadier.LiteralMessage;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.block.ModBlocks;

public class ModCreativeModeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineCiv.MOD_ID);

public static final RegistryObject<CreativeModeTab> ALEXANDRITE_BLOCKS_TAB = CREATIVE_MODE_TABS.register("monument_block_tab",
        () -> CreativeModeTab.builder().icon(() -> new ItemStack(ModBlocks.MONUMENT_BLOCK.get()))
                .title(Component.translatable("creativetab.mineciv.monument_block"))
                .displayItems((itemDisplayParameters, output) -> {
                    output.accept(ModBlocks.MONUMENT_BLOCK.get());

                }).build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
