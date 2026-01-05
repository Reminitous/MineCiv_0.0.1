package net.reminitous.mineciv.registry;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.monument.MonumentBlock;

public final class ModBlocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MineCiv.MOD_ID);

    public static final RegistryObject<Block> MONUMENT = BLOCKS.register(
            "monument",
            () -> new MonumentBlock(
                    BlockBehaviour.Properties.of()
                            .strength(5.0F, 1200.0F)
                            .sound(SoundType.STONE)
                            .requiresCorrectToolForDrops()
            )
    );

    private ModBlocks() {}
}
