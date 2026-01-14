package net.reminitous.mineciv.war.loot;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.war.WarTaxUtil;

import java.util.UUID;

public final class WarTaxLootModifier extends LootModifier {

    public static final MapCodec<WarTaxLootModifier> CODEC = RecordCodecBuilder.mapCodec(inst ->
            LootModifier.codecStart(inst).apply(inst, WarTaxLootModifier::new)
    );

    public WarTaxLootModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        Player player = findPlayer(context);
        if (player == null) return generatedLoot;
        if (!(player.level() instanceof ServerLevel level)) return generatedLoot;

        CivSavedData civData = CivSavedData.get(level.getServer());
        UUID producerCivId = civData.getPlayersCiv(player.getUUID());
        if (producerCivId == null) return generatedLoot;

        WarTaxUtil.TaxInfo tax = WarTaxUtil.getActiveTax(level, producerCivId);
        if (!tax.active()) return generatedLoot;

        ObjectArrayList<ItemStack> kept = new ObjectArrayList<>();

        for (ItemStack stack : generatedLoot) {
            if (stack == null || stack.isEmpty()) continue;

            int taxCount = WarTaxUtil.computeTaxCount(stack.getCount(), tax.bps());
            if (taxCount <= 0) {
                kept.add(stack);
                continue;
            }

            int keepCount = stack.getCount() - taxCount;

            if (keepCount > 0) {
                ItemStack keep = stack.copy();
                keep.setCount(keepCount);
                kept.add(keep);
            }

            ItemStack taxedStack = stack.copy();
            taxedStack.setCount(taxCount);
            WarTaxUtil.transferToWinner(level, tax.winnerCivId(), taxedStack);
        }

        return kept;
    }

    private static Player findPlayer(LootContext ctx) {
        Entity thisEnt = ctx.getParamOrNull(LootContextParams.THIS_ENTITY);
        if (thisEnt instanceof Player p) return p;

        Entity lastDamage = ctx.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER);
        if (lastDamage instanceof Player p) return p;

        Entity attacking = ctx.getParamOrNull(LootContextParams.ATTACKING_ENTITY);
        if (attacking instanceof Player p) return p;

        return null;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
