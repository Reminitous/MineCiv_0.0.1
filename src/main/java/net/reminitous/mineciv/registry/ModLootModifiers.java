package net.reminitous.mineciv.registry;

import com.mojang.serialization.MapCodec;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.war.loot.WarTaxLootModifier;

public final class ModLootModifiers {

    // In 1.21.x, the registry holds MapCodec<? extends IGlobalLootModifier>
    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MineCiv.MOD_ID);

    // Register your modifier codec
    public static final RegistryObject<MapCodec<? extends IGlobalLootModifier>> WAR_TAX =
            LOOT_MODIFIERS.register("war_tax", () -> WarTaxLootModifier.CODEC);

    private ModLootModifiers() {}
}
