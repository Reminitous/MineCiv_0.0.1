package net.reminitous.mineciv.util;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

import java.util.List;

public final class BlockDropUtil {

    private BlockDropUtil() {}

    public static List<ItemStack> getDrops(ServerLevel level, BlockPos pos, BlockState state, LivingEntity harvester) {
        BlockEntity be = level.getBlockEntity(pos);

        LootParams.Builder builder = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, harvester.position())
                .withParameter(LootContextParams.TOOL, harvester.getMainHandItem())
                .withOptionalParameter(LootContextParams.THIS_ENTITY, harvester)
                .withOptionalParameter(LootContextParams.BLOCK_ENTITY, be);

        return state.getDrops(builder);

    }
}
