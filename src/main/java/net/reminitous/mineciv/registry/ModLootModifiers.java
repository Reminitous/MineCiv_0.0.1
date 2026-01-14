package net.reminitous.mineciv.registry;

import com.mojang.serialization.MapCodec;

import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.war.loot.WarTaxLootModifier;

public final class ModLootModifiers {

    private ModLootModifiers() {}

    public static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, MineCiv.MOD_ID);

    public static final RegistryObject<MapCodec<WarTaxLootModifier>> WAR_TAX =
            LOOT_MODIFIERS.register("war_tax", () -> WarTaxLootModifier.CODEC);
}
