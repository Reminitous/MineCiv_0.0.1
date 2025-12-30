package net.reminitous.mineciv.villager;

import com.google.common.collect.ImmutableSet;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.block.ModBlocks;

public class ModVillagers {

    public static final DeferredRegister<PoiType> POI_TYPES =
            DeferredRegister.create(ForgeRegistries.POI_TYPES, MineCiv.MOD_ID);

    public static final DeferredRegister<VillagerProfession> VILLAGER_PROFESSIONS =
            DeferredRegister.create(ForgeRegistries.VILLAGER_PROFESSIONS, MineCiv.MOD_ID);

    // Jobsite POI (your monument block)
    public static final RegistryObject<PoiType> VILLAGER_POI = POI_TYPES.register("kaupen_poi",
            () -> new PoiType(
                    ImmutableSet.copyOf(ModBlocks.MONUMENT_BLOCK.get().getStateDefinition().getPossibleStates()),
                    10,
                    16
            )
    );

    public static final RegistryObject<VillagerProfession> CULTIVATOR =
            VILLAGER_PROFESSIONS.register("cultivator",
                    () -> new VillagerProfession(
                            "cultivator",
                            holder -> holder.value() == VILLAGER_POI.get(),
                            holder -> holder.value() == VILLAGER_POI.get(),
                            ImmutableSet.of(
                                    Items.WHEAT,
                                    Items.WHEAT_SEEDS,
                                    Items.BEETROOT_SEEDS,
                                    Items.BONE_MEAL,
                                    Items.PUMPKIN_SEEDS,
                                    Items.MELON_SEEDS,
                                    Items.APPLE,
                                    Items.MELON_SLICE,
                                    Items.CARROT,
                                    Items.POTATO,
                                    Items.SWEET_BERRIES,
                                    Items.BEETROOT,
                                    Items.GLOW_BERRIES
                            ),
                            ImmutableSet.of(Blocks.FARMLAND),
                            SoundEvents.VILLAGER_WORK_FARMER
                    )
            );

    // NEW profession
    public static final RegistryObject<VillagerProfession> LUMBERJACK =
            VILLAGER_PROFESSIONS.register("lumberjack",
                    () -> new VillagerProfession(
                            "lumberjack",
                            holder -> holder.value() == VILLAGER_POI.get(),
                            holder -> holder.value() == VILLAGER_POI.get(),
                            ImmutableSet.of(
                                    Items.OAK_LOG,
                                    Items.SPRUCE_LOG,
                                    Items.BIRCH_LOG,
                                    Items.JUNGLE_LOG,
                                    Items.ACACIA_LOG,
                                    Items.DARK_OAK_LOG,
                                    Items.MANGROVE_LOG,
                                    Items.CHERRY_LOG,
                                    Items.BAMBOO,
                                    Items.STICK
                            ),
                            ImmutableSet.of(
                                    Blocks.OAK_LOG,
                                    Blocks.SPRUCE_LOG,
                                    Blocks.BIRCH_LOG,
                                    Blocks.JUNGLE_LOG,
                                    Blocks.ACACIA_LOG,
                                    Blocks.DARK_OAK_LOG,
                                    Blocks.MANGROVE_LOG,
                                    Blocks.CHERRY_LOG
                            ),
                            SoundEvents.VILLAGER_WORK_FARMER
                    )
            );

    public static void register(IEventBus eventBus) {
        POI_TYPES.register(eventBus);
        VILLAGER_PROFESSIONS.register(eventBus);
    }
}
