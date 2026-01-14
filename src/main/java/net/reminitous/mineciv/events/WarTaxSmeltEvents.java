package net.reminitous.mineciv.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.war.WarTaxUtil;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class WarTaxSmeltEvents {

    private WarTaxSmeltEvents() {}

    @SubscribeEvent
    public static void onItemSmelted(PlayerEvent.ItemSmeltedEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        ItemStack out = e.getSmelting();
        if (out == null || out.isEmpty()) return;

        CivSavedData civData = CivSavedData.get(level.getServer());
        UUID producerCivId = civData.getPlayersCiv(player.getUUID());
        if (producerCivId == null) return;

        WarTaxUtil.TaxInfo tax = WarTaxUtil.getActiveTax(level, producerCivId);
        if (!tax.active()) return;

        int taxCount = WarTaxUtil.computeTaxCount(out.getCount(), tax.bps());
        if (taxCount <= 0) return;
        if (taxCount >= out.getCount()) taxCount = out.getCount();

        // remove from player's smelt output stack and give to winner
        out.shrink(taxCount);

        ItemStack taxed = out.copy();
        taxed.setCount(taxCount);

        WarTaxUtil.transferToWinner(level, tax.winnerCivId(), taxed);
    }
}
