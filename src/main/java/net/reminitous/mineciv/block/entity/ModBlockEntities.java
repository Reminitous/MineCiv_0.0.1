package net.reminitous.mineciv.block.entity;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.block.ModBlocks;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MineCiv.MOD_ID);

    public static final RegistryObject<BlockEntityType<MonumentBlockEntity>> MONUMENT_BE =
            BLOCK_ENTITIES.register("monument",
                    () -> BlockEntityType.Builder.of(MonumentBlockEntity::new, ModBlocks.MONUMENT_BLOCK.get())
                            .build(null));

    public static void register(IEventBus bus) {
        BLOCK_ENTITIES.register(bus);
    }
}
