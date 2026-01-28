package net.reminitous.mineciv.registry;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import net.reminitous.mineciv.MineCiv;

// NPC classes
import net.reminitous.mineciv.npc.MineCivArcherNpc;
import net.reminitous.mineciv.npc.MineCivEnchanterNpc;
import net.reminitous.mineciv.npc.MineCivFarmerNpc;
import net.reminitous.mineciv.npc.MineCivKnightNpc;
import net.reminitous.mineciv.npc.MineCivLumberjackNpc;
import net.reminitous.mineciv.npc.MineCivMinerNpc;
import net.reminitous.mineciv.npc.MineCivPatrolNpc;
import net.reminitous.mineciv.npc.MineCivShepherdNpc;
import net.reminitous.mineciv.npc.MineCivWitchNpc;
import net.reminitous.mineciv.npc.MineCivWizardNpc;
import net.reminitous.mineciv.npc.MineCivWorkerNpc;

public final class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, MineCiv.MOD_ID);

    // --- Combat / Guards ---
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

    public static final RegistryObject<EntityType<MineCivPatrolNpc>> NPC_PATROL =
            ENTITY_TYPES.register("npc_patrol", () ->
                    EntityType.Builder.of(MineCivPatrolNpc::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MineCiv.MOD_ID + ":npc_patrol")
            );

    // --- Workers ---
    public static final RegistryObject<EntityType<MineCivFarmerNpc>> NPC_FARMER =
            ENTITY_TYPES.register("npc_farmer", () ->
                    EntityType.Builder.of(MineCivFarmerNpc::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MineCiv.MOD_ID + ":npc_farmer")
            );

    public static final RegistryObject<EntityType<MineCivLumberjackNpc>> NPC_LUMBERJACK =
            ENTITY_TYPES.register("npc_lumberjack", () ->
                    EntityType.Builder.of(MineCivLumberjackNpc::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MineCiv.MOD_ID + ":npc_lumberjack")
            );

    public static final RegistryObject<EntityType<MineCivMinerNpc>> NPC_MINER =
            ENTITY_TYPES.register("npc_miner", () ->
                    EntityType.Builder.of(MineCivMinerNpc::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MineCiv.MOD_ID + ":npc_miner")
            );

    public static final RegistryObject<EntityType<MineCivWorkerNpc>> NPC_WORKER =
            ENTITY_TYPES.register("npc_worker", () ->
                    EntityType.Builder.of(MineCivWorkerNpc::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MineCiv.MOD_ID + ":npc_worker")
            );

    public static final RegistryObject<EntityType<MineCivShepherdNpc>> NPC_SHEPHERD =
            ENTITY_TYPES.register("npc_shepherd", () ->
                    EntityType.Builder.of(MineCivShepherdNpc::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MineCiv.MOD_ID + ":npc_shepherd")
            );

    // --- Magic roles ---
    public static final RegistryObject<EntityType<MineCivWitchNpc>> NPC_WITCH =
            ENTITY_TYPES.register("npc_witch", () ->
                    EntityType.Builder.of(MineCivWitchNpc::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MineCiv.MOD_ID + ":npc_witch")
            );

    public static final RegistryObject<EntityType<MineCivWizardNpc>> NPC_WIZARD =
            ENTITY_TYPES.register("npc_wizard", () ->
                    EntityType.Builder.of(MineCivWizardNpc::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MineCiv.MOD_ID + ":npc_wizard")
            );

    public static final RegistryObject<EntityType<MineCivEnchanterNpc>> NPC_ENCHANTER =
            ENTITY_TYPES.register("npc_enchanter", () ->
                    EntityType.Builder.of(MineCivEnchanterNpc::new, MobCategory.CREATURE)
                            .sized(0.6F, 1.95F)
                            .clientTrackingRange(8)
                            .build(MineCiv.MOD_ID + ":npc_enchanter")
            );

    private ModEntities() {}

    public static void register(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
    }

    /** Call from an EntityAttributeCreationEvent listener on the MOD bus */
    public static void onAttributes(EntityAttributeCreationEvent e) {
        e.put(NPC_ARCHER.get(), MineCivArcherNpc.createAttributes().build());
        e.put(NPC_KNIGHT.get(), MineCivKnightNpc.createAttributes().build());
        e.put(NPC_PATROL.get(), MineCivPatrolNpc.createAttributes().build());

        e.put(NPC_FARMER.get(), MineCivFarmerNpc.createAttributes().build());
        e.put(NPC_LUMBERJACK.get(), MineCivLumberjackNpc.createAttributes().build());
        e.put(NPC_MINER.get(), MineCivMinerNpc.createAttributes().build());
        e.put(NPC_WORKER.get(), MineCivWorkerNpc.createAttributes().build());
        e.put(NPC_SHEPHERD.get(), MineCivShepherdNpc.createAttributes().build());

        e.put(NPC_WITCH.get(), MineCivWitchNpc.createAttributes().build());
        e.put(NPC_WIZARD.get(), MineCivWizardNpc.createAttributes().build());
        e.put(NPC_ENCHANTER.get(), MineCivEnchanterNpc.createAttributes().build());
    }
}
