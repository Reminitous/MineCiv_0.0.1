package net.reminitous.mineciv.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.entity.custom.KnightNPC; // ✅ REQUIRED

public class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MineCiv.MOD_ID);

    public static final RegistryObject<EntityType<KnightNPC>> KNIGHT =
            ENTITY_TYPES.register("knight", () ->
                    EntityType.Builder.<KnightNPC>of(KnightNPC::new, MobCategory.MISC)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build("knight")
            );

    public static void register(IEventBus bus) {
        ENTITY_TYPES.register(bus);
    }
}
