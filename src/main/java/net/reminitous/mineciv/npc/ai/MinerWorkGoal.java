package net.reminitous.mineciv.npc.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.npc.MineCivMinerNpc;
import net.reminitous.mineciv.util.BlockDropUtil;
import net.reminitous.mineciv.util.CivTerritoryUtil;
import net.reminitous.mineciv.util.InventoryUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class MinerWorkGoal extends Goal {

    // Forge-safe replacement for BlockTags.ORES
    private static final TagKey<Block> ORES =
            TagKey.create(Registries.BLOCK, ResourceLocation.withDefaultNamespace("ores"));

    private final MineCivMinerNpc npc;
    private final double speed;
    private final int oreSearchRadius;
    private final int chestSearchRadius;

    private int cooldown = 0;
    private BlockPos targetOre = null;

    public MinerWorkGoal(MineCivMinerNpc npc, double speed, int oreSearchRadius, int chestSearchRadius) {
        this.npc = npc;
        this.speed = speed;
        this.oreSearchRadius = Math.max(8, oreSearchRadius);
        this.chestSearchRadius = Math.max(8, chestSearchRadius);
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(npc.level() instanceof ServerLevel)) return false;
        if (npc.getCivId() == null) return false;

        if (cooldown-- > 0) return false;
        cooldown = 20;

        Civilization civ = getCiv();
        if (civ == null) return false;
        if (!CivTerritoryUtil.isInTerritory(civ, npc.blockPosition())) return false;

        targetOre = findExposedOre(civ);
        if (targetOre != null) return true;

        // fallback: “dig forward” a little
        targetOre = npc.blockPosition().relative(npc.getDirection(), 6);
        return CivTerritoryUtil.isInTerritory(civ, targetOre);
    }

    @Override
    public void start() {
        if (targetOre != null) {
            npc.getNavigation().moveTo(
                    targetOre.getX() + 0.5,
                    targetOre.getY(),
                    targetOre.getZ() + 0.5,
                    speed
            );
        }
    }

    @Override
    public boolean canContinueToUse() {
        return targetOre != null && !npc.getNavigation().isDone();
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;
        Civilization civ = getCiv();
        if (civ == null || targetOre == null) return;

        if (!CivTerritoryUtil.isInTerritory(civ, targetOre)) {
            targetOre = null;
            return;
        }

        double d2 = npc.distanceToSqr(
                targetOre.getX() + 0.5,
                targetOre.getY() + 0.5,
                targetOre.getZ() + 0.5
        );

        if (d2 <= 2.7D * 2.7D) {
            mineAt(level, civ, targetOre);
            targetOre = null;
        }
    }

    /* ---------------- Mining ---------------- */

    private void mineAt(ServerLevel level, Civilization civ, BlockPos pos) {
        BlockState st = level.getBlockState(pos);

        boolean mineable =
                st.is(ORES)
                        || st.is(BlockTags.STONE_ORE_REPLACEABLES)
                        || st.is(BlockTags.BASE_STONE_OVERWORLD);

        if (!mineable) return;

        List<ItemStack> drops = BlockDropUtil.getDrops(level, pos, st, npc);
        level.removeBlock(pos, false);

        List<BlockPos> containers = findNearbyContainers(level, civ, npc.blockPosition(), chestSearchRadius);
        List<ItemStack> leftovers = storeAll(level, containers, drops);

        for (ItemStack rem : leftovers) {
            if (!rem.isEmpty()) npc.spawnAtLocation(rem);
        }
    }

    private BlockPos findExposedOre(Civilization civ) {
        if (!(npc.level() instanceof ServerLevel level)) return null;

        BlockPos center = npc.blockPosition();
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;

        for (int dy = -6; dy <= 6; dy++) {
            for (int dx = -oreSearchRadius; dx <= oreSearchRadius; dx++) {
                for (int dz = -oreSearchRadius; dz <= oreSearchRadius; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!CivTerritoryUtil.isInTerritory(civ, p)) continue;

                    BlockState st = level.getBlockState(p);
                    if (!st.is(ORES)) continue;

                    if (!isExposed(level, p)) continue;

                    double d2 = p.distToCenterSqr(
                            center.getX() + 0.5,
                            center.getY() + 0.5,
                            center.getZ() + 0.5
                    );

                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = p;
                    }
                }
            }
        }

        return best;
    }

    private boolean isExposed(ServerLevel level, BlockPos p) {
        return level.getBlockState(p.north()).isAir()
                || level.getBlockState(p.south()).isAir()
                || level.getBlockState(p.east()).isAir()
                || level.getBlockState(p.west()).isAir()
                || level.getBlockState(p.above()).isAir()
                || level.getBlockState(p.below()).isAir();
    }

    /* ---------------- Storage ---------------- */

    private List<BlockPos> findNearbyContainers(ServerLevel level, Civilization civ, BlockPos center, int radius) {
        List<BlockPos> out = new ArrayList<>();

        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!CivTerritoryUtil.isInTerritory(civ, p)) continue;

                    BlockEntity be = level.getBlockEntity(p);
                    if (be instanceof Container) out.add(p);
                }
            }
        }

        return out;
    }

    private List<ItemStack> storeAll(ServerLevel level, List<BlockPos> containers, List<ItemStack> drops) {
        List<ItemStack> leftover = new ArrayList<>();

        for (ItemStack stack : drops) {
            if (stack == null || stack.isEmpty()) continue;

            ItemStack rem = stack.copy();
            for (BlockPos pos : containers) {
                BlockEntity be = level.getBlockEntity(pos);
                if (!(be instanceof Container c)) continue;

                rem = InventoryUtil.insert(c, rem);
                if (rem.isEmpty()) break;
            }

            if (!rem.isEmpty()) leftover.add(rem);
        }

        return leftover;
    }

    /* ---------------- Civ lookup ---------------- */

    private Civilization getCiv() {
        if (!(npc.level() instanceof ServerLevel level)) return null;
        CivSavedData data = CivSavedData.get(level.getServer());
        return data.civs().get(npc.getCivId());
    }
}
