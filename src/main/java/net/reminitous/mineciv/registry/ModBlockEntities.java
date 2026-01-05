package net.reminitous.mineciv.registry;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.monument.MonumentBlockEntity;

public final class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MineCiv.MOD_ID);

    public static final RegistryObject<BlockEntityType<MonumentBlockEntity>> MONUMENT =
            BLOCK_ENTITIES.register(
                    "monument",
                    () -> BlockEntityType.Builder
                            .of(
                                    MonumentBlockEntity::new,
                                    ModBlocks.MONUMENT.getHolder().orElseThrow().value()
                            )
                            .build(null)
            );


    private ModBlockEntities() {}
}
