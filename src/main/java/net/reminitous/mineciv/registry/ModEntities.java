package net.reminitous.mineciv.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.npc.MineCivArcherNpc;
import net.reminitous.mineciv.npc.MineCivFarmerNpc;
import net.reminitous.mineciv.npc.MineCivKnightNpc;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MineCiv.MOD_ID);

    public static final RegistryObject<EntityType<MineCivArcherNpc>> NPC_ARCHER =
            ENTITY_TYPES.register("npc_archer", () ->
                    EntityType.Builder.of(MineCivArcherNpc::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MineCiv.MOD_ID + ":npc_archer")
            );

    public static final RegistryObject<EntityType<MineCivKnightNpc>> NPC_KNIGHT =
            ENTITY_TYPES.register("npc_knight", () ->
                    EntityType.Builder.of(MineCivKnightNpc::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MineCiv.MOD_ID + ":npc_knight")
            );

    public static final RegistryObject<EntityType<MineCivFarmerNpc>> NPC_FARMER =
            ENTITY_TYPES.register("npc_farmer", () ->
                    EntityType.Builder.of(MineCivFarmerNpc::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MineCiv.MOD_ID + ":npc_farmer")
            );

    private ModEntities() {}

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }

    /** Call from an EntityAttributeCreationEvent listener on the MOD bus */
    public static void onAttributes(EntityAttributeCreationEvent e) {
        e.put(NPC_ARCHER.get(), MineCivArcherNpc.createAttributes().build());
        e.put(NPC_KNIGHT.get(), MineCivKnightNpc.createAttributes().build());
        e.put(NPC_FARMER.get(), MineCivFarmerNpc.createAttributes().build());
    }
}
