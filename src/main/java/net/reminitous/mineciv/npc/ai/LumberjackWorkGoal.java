package net.reminitous.mineciv.npc.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.npc.MineCivLumberjackNpc;
import net.reminitous.mineciv.util.BlockDropUtil;
import net.reminitous.mineciv.util.CivTerritoryUtil;
import net.reminitous.mineciv.util.InventoryUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class LumberjackWorkGoal extends Goal {

    private final MineCivLumberjackNpc npc;
    private final double speed;
    private final int searchRadius;
    private final int chestSearchRadius;

    private int cooldown = 0;
    private BlockPos targetLog = null;

    public LumberjackWorkGoal(MineCivLumberjackNpc npc, double speed, int searchRadius, int chestSearchRadius) {
        this.npc = npc;
        this.speed = speed;
        this.searchRadius = Math.max(6, searchRadius);
        this.chestSearchRadius = Math.max(6, chestSearchRadius);
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(npc.level() instanceof ServerLevel)) return false;
        if (npc.getCivId() == null) return false;

        if (cooldown-- > 0) return false;
        cooldown = 20; // once per second

        Civilization civ = getCiv();
        if (civ == null) return false;

        // Only work if we're in territory
        if (!CivTerritoryUtil.isInTerritory(civ, npc.blockPosition())) return false;

        targetLog = findNearestLog(civ);
        return targetLog != null;
    }

    @Override
    public void start() {
        if (targetLog != null) {
            npc.getNavigation().moveTo(targetLog.getX() + 0.5, targetLog.getY(), targetLog.getZ() + 0.5, speed);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return targetLog != null && !npc.getNavigation().isDone();
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;

        Civilization civ = getCiv();
        if (civ == null || targetLog == null) return;

        // If log disappeared / not in territory anymore, abort
        if (!CivTerritoryUtil.isInTerritory(civ, targetLog)) {
            targetLog = null;
            return;
        }

        BlockState st = level.getBlockState(targetLog);
        if (!st.is(BlockTags.LOGS)) {
            targetLog = null;
            return;
        }

        double d2 = npc.distanceToSqr(targetLog.getX() + 0.5, targetLog.getY() + 0.5, targetLog.getZ() + 0.5);
        if (d2 <= 2.5D * 2.5D) {
            chopTreeAt(level, civ, targetLog);
            targetLog = null;
        }
    }

    /* ---------------- Core behavior ---------------- */

    private void chopTreeAt(ServerLevel level, Civilization civ, BlockPos startLog) {
        // v1: remove a connected “blob” of logs/leaves around the start log (bounded)
        // This reliably handles player-planted trees and natural trees without expensive full tree algorithms.
        int maxBlocksToBreak = 128;

        List<BlockPos> containers = findNearbyContainers(level, civ, npc.blockPosition(), chestSearchRadius);
        List<ItemStack> collectedDrops = new ArrayList<>();

        List<BlockPos> queue = new ArrayList<>();
        queue.add(startLog);

        int idx = 0;
        while (idx < queue.size() && maxBlocksToBreak > 0) {
            BlockPos p = queue.get(idx++);
            if (!CivTerritoryUtil.isInTerritory(civ, p)) continue;

            BlockState s = level.getBlockState(p);
            boolean isLog = s.is(BlockTags.LOGS);
            boolean isLeaf = s.is(BlockTags.LEAVES);

            if (!isLog && !isLeaf) continue;

            // If leaf: only break if it’s adjacent to a log somewhere in this blob,
            // but v1 just allows leaves to be broken while expanding from logs.
            // We prevent runaway leaf-clearing by limiting maxBlocksToBreak.

            // Collect drops
            collectedDrops.addAll(BlockDropUtil.getDrops(level, p, s, npc));
            level.removeBlock(p, false);
            maxBlocksToBreak--;

            // Expand neighbors
            for (BlockPos n : neighbors26(p)) {
                if (queue.contains(n)) continue;
                BlockState ns = level.getBlockState(n);
                if (ns.is(BlockTags.LOGS) || ns.is(BlockTags.LEAVES)) {
                    queue.add(n);
                }
            }
        }

        // Deposit drops into chests (territory only). Leftovers drop at NPC.
        List<ItemStack> leftovers = storeAll(level, containers, collectedDrops);
        for (ItemStack rem : leftovers) {
            if (!rem.isEmpty()) npc.spawnAtLocation(rem);
        }
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

    /* ---------------- Searches ---------------- */

    private BlockPos findNearestLog(Civilization civ) {
        if (!(npc.level() instanceof ServerLevel level)) return null;

        BlockPos center = npc.blockPosition();
        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;

        for (int dy = -4; dy <= 8; dy++) {
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!CivTerritoryUtil.isInTerritory(civ, p)) continue;

                    BlockState st = level.getBlockState(p);
                    if (!st.is(BlockTags.LOGS)) continue;

                    double d2 = p.distToCenterSqr(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
                    if (d2 < bestD2) {
                        bestD2 = d2;
                        best = p;
                    }
                }
            }
        }

        return best;
    }

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

    private Civilization getCiv() {
        if (!(npc.level() instanceof ServerLevel level)) return null;
        CivSavedData data = CivSavedData.get(level.getServer());
        return data.civs().get(npc.getCivId());
    }

    private static List<BlockPos> neighbors26(BlockPos p) {
        List<BlockPos> out = new ArrayList<>(26);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dy == 0 && dz == 0) continue;
                    out.add(p.offset(dx, dy, dz));
                }
            }
        }
        return out;
    }
}
