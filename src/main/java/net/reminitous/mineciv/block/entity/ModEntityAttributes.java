package net.reminitous.mineciv.block.entity;

import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.entity.ModEntities;
import net.reminitous.mineciv.entity.custom.KnightNPC;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ModEntityAttributes {

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntities.KNIGHT.get(), KnightNPC.createAttributes().build());
    }
}
