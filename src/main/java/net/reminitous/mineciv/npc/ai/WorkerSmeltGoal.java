package net.reminitous.mineciv.npc.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.npc.MineCivWorkerNpc;
import net.reminitous.mineciv.util.CivTerritoryUtil;
import net.reminitous.mineciv.util.InventoryUtil;
import net.reminitous.mineciv.util.SmeltableUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class WorkerSmeltGoal extends Goal {

    private final MineCivWorkerNpc npc;
    private final double speed;
    private final int scanRadius;
    private final int chestScanRadius;

    private int cooldown = 0;
    private BlockPos targetFurnacePos = null;

    public WorkerSmeltGoal(MineCivWorkerNpc npc, double speed, int scanRadius, int chestScanRadius) {
        this.npc = npc;
        this.speed = speed;
        this.scanRadius = Math.max(8, scanRadius);
        this.chestScanRadius = Math.max(8, chestScanRadius);
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(npc.level() instanceof ServerLevel level)) return false;
        if (npc.getCivId() == null) return false;

        if (cooldown-- > 0) return false;
        cooldown = 20; // once/sec

        Civilization civ = getCiv(level);
        if (civ == null) return false;

        if (!CivTerritoryUtil.isInTerritory(civ, npc.blockPosition())) return false;

        targetFurnacePos = findNearbyFurnace(level, civ);
        return targetFurnacePos != null;
    }

    @Override
    public void start() {
        if (targetFurnacePos != null) {
            npc.getNavigation().moveTo(
                    targetFurnacePos.getX() + 0.5,
                    targetFurnacePos.getY(),
                    targetFurnacePos.getZ() + 0.5,
                    speed
            );
        }
    }

    @Override
    public boolean canContinueToUse() {
        return targetFurnacePos != null && !npc.getNavigation().isDone();
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;
        Civilization civ = getCiv(level);
        if (civ == null || targetFurnacePos == null) return;

        if (!CivTerritoryUtil.isInTerritory(civ, targetFurnacePos)) {
            targetFurnacePos = null;
            return;
        }

        double d2 = npc.distanceToSqr(
                targetFurnacePos.getX() + 0.5,
                targetFurnacePos.getY() + 0.5,
                targetFurnacePos.getZ() + 0.5
        );

        if (d2 > 2.8D * 2.8D) return;

        BlockEntity be = level.getBlockEntity(targetFurnacePos);
        if (!(be instanceof AbstractFurnaceBlockEntity furnace)) {
            targetFurnacePos = null;
            return;
        }

        // 1) Ensure fuel slot has coal (unlimited)
        topUpFuel(furnace);

        // 2) If output slot has items, move to chests
        moveOutputToChests(level, civ, furnace);

        // 3) If input slot empty (or low), pull smeltables from chests and insert
        fillInputFromChests(level, civ, furnace, targetFurnacePos);

        // Done for now
        targetFurnacePos = null;
    }

    /* ---------------- Furnace ops ---------------- */

    /**
     * Fuel slot index 1 for AbstractFurnaceBlockEntity:
     * 0=input, 1=fuel, 2=output
     */
    private void topUpFuel(AbstractFurnaceBlockEntity furnace) {
        ItemStack fuel = furnace.getItem(1);
        if (fuel.isEmpty() || fuel.getCount() < 8) {
            furnace.setItem(1, new ItemStack(Items.COAL, 64));
            furnace.setChanged();
        }
    }

    private void moveOutputToChests(ServerLevel level, Civilization civ, AbstractFurnaceBlockEntity furnace) {
        ItemStack out = furnace.getItem(2);
        if (out.isEmpty()) return;

        List<BlockPos> containers = findNearbyContainers(level, civ, npc.blockPosition(), chestScanRadius);

        ItemStack rem = out.copy();
        rem = insertIntoAny(level, containers, rem);

        // Whatever got inserted, remove from furnace output
        int inserted = out.getCount() - rem.getCount();
        if (inserted > 0) {
            out.shrink(inserted);
            if (out.isEmpty()) furnace.setItem(2, ItemStack.EMPTY);
            furnace.setChanged();
        }

        // If chests full, let it remain in output; no dropping spam.
    }

    private void fillInputFromChests(ServerLevel level, Civilization civ, AbstractFurnaceBlockEntity furnace, BlockPos furnacePos) {
        ItemStack in = furnace.getItem(0);

        // If input already has stuff, don't override in v1
        if (!in.isEmpty()) return;

        boolean isBlast = level.getBlockState(furnacePos).getBlock() instanceof BlastFurnaceBlock;

        List<BlockPos> containers = findNearbyContainers(level, civ, npc.blockPosition(), chestScanRadius);

        // Find a smeltable stack in chests
        for (BlockPos pos : containers) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container c)) continue;

            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (s.isEmpty()) continue;

                boolean smeltable = isBlast
                        ? SmeltableUtil.isSmeltableInBlastFurnace(level, s)
                        : SmeltableUtil.isSmeltableInFurnace(level, s);

                if (!smeltable) continue;

                // Pull up to 16 items at a time
                int take = Math.min(16, s.getCount());
                ItemStack pulled = s.copy();
                pulled.setCount(take);

                s.shrink(take);
                if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
                c.setChanged();

                furnace.setItem(0, pulled);
                furnace.setChanged();
                return;
            }
        }
    }

    /* ---------------- Scans ---------------- */

    private BlockPos findNearbyFurnace(ServerLevel level, Civilization civ) {
        BlockPos center = npc.blockPosition();

        BlockPos best = null;
        double bestD2 = Double.MAX_VALUE;

        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -scanRadius; dx <= scanRadius; dx++) {
                for (int dz = -scanRadius; dz <= scanRadius; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!CivTerritoryUtil.isInTerritory(civ, p)) continue;

                    BlockState st = level.getBlockState(p);
                    if (!(st.getBlock() instanceof AbstractFurnaceBlock)) continue;

                    BlockEntity be = level.getBlockEntity(p);
                    if (!(be instanceof AbstractFurnaceBlockEntity furnace)) continue;

                    // Prefer furnaces that have output ready OR are missing fuel OR are missing input
                    boolean needsAttention =
                            furnace.getItem(2).getCount() > 0 ||
                                    furnace.getItem(1).isEmpty() || furnace.getItem(1).getCount() < 8 ||
                                    furnace.getItem(0).isEmpty();

                    if (!needsAttention) continue;

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

    private ItemStack insertIntoAny(ServerLevel level, List<BlockPos> containers, ItemStack stack) {
        ItemStack rem = stack;
        for (BlockPos pos : containers) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container c)) continue;

            rem = InventoryUtil.insert(c, rem);
            if (rem.isEmpty()) return ItemStack.EMPTY;
        }
        return rem;
    }

    private Civilization getCiv(ServerLevel level) {
        CivSavedData data = CivSavedData.get(level.getServer());
        return data.civs().get(npc.getCivId());
    }
}
