package net.reminitous.mineciv.registry;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.monument.MonumentBlock;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MineCiv.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, MineCiv.MOD_ID);

    public static final RegistryObject<Block> MONUMENT = BLOCKS.register("monument",
            () -> new MonumentBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(3.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
            )
    );

    public static final RegistryObject<Item> MONUMENT_ITEM = ITEMS.register("monument",
            () -> new BlockItem(MONUMENT.get(), new Item.Properties())
    );

    private ModBlocks() {}
}
