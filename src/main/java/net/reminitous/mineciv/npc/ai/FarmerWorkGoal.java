package net.reminitous.mineciv.npc.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.util.CivTerritoryUtil;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.npc.MineCivFarmerNpc;
import net.reminitous.mineciv.util.CropDropUtil;
import net.reminitous.mineciv.util.InventoryUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class FarmerWorkGoal extends Goal {

    private final MineCivFarmerNpc farmer;
    private final double speed;
    private final int cropSearchRadius;
    private final int chestSearchRadius;

    private int cooldownTicks = 0;
    private BlockPos targetCropPos = null;

    public FarmerWorkGoal(MineCivFarmerNpc farmer, double speed, int cropSearchRadius, int chestSearchRadius) {
        this.farmer = farmer;
        this.speed = speed;
        this.cropSearchRadius = Math.max(4, cropSearchRadius);
        this.chestSearchRadius = Math.max(4, chestSearchRadius);
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(farmer.level() instanceof ServerLevel)) return false;
        if (farmer.getCivId() == null) return false;
        if (farmer.getHomeMonument() == null) return false;

        if (cooldownTicks-- > 0) return false;
        cooldownTicks = 20; // check once per second

        Civilization civ = getCiv();
        if (civ == null) return false;
        if (!CivTerritoryUtil.isInTerritory(civ, farmer.blockPosition())) return false;

        targetCropPos = findMatureCrop(civ);
        return targetCropPos != null;
    }

    @Override
    public boolean canContinueToUse() {
        return targetCropPos != null && !farmer.getNavigation().isDone();
    }

    @Override
    public void start() {
        if (targetCropPos != null) {
            farmer.getNavigation().moveTo(
                    targetCropPos.getX() + 0.5,
                    targetCropPos.getY(),
                    targetCropPos.getZ() + 0.5,
                    speed
            );
        }
    }

    @Override
    public void tick() {
        if (!(farmer.level() instanceof ServerLevel level)) return;
        Civilization civ = getCiv();
        if (civ == null || targetCropPos == null) return;

        BlockState st = level.getBlockState(targetCropPos);
        if (!(st.getBlock() instanceof CropBlock crop) || !crop.isMaxAge(st)) {
            targetCropPos = null;
            return;
        }

        if (farmer.distanceToSqr(
                targetCropPos.getX() + 0.5,
                targetCropPos.getY() + 0.5,
                targetCropPos.getZ() + 0.5
        ) <= 2.5D * 2.5D) {

            harvestAndHandle(level, civ, targetCropPos, crop, st);
            targetCropPos = null;
        }
    }

    /* ---------------- Core behavior ---------------- */

    private void harvestAndHandle(ServerLevel level, Civilization civ, BlockPos cropPos, CropBlock crop, BlockState cropState) {
        List<ItemStack> drops = CropDropUtil.getDrops(level, cropPos, cropState, farmer);

        // Remove crop
        level.removeBlock(cropPos, false);

        // 1.21-safe: get the seed/planting item via clone stack (pick-block)
        ItemStack seedStack = crop.getCloneItemStack(level, cropPos, cropState);
        Item seedItem = seedStack.isEmpty() ? null : seedStack.getItem();

        boolean replanted = false;

        // Only attempt replant if we have a valid seed item AND can consume one from chests
        if (seedItem != null) {
            replanted = tryConsumeSeedFromChests(level, civ, seedItem);
        }

        if (replanted && seedItem != null) {
            // Deduct one seed from drops so we don't duplicate (we already "used" a seed from storage)
            consumeOneSeedFromDrops(drops, seedItem);

            // Replant age 0
            level.setBlock(cropPos, crop.getStateForAge(0), 3);
        }

        // Store drops in territory chests; leftovers drop at farmer
        List<BlockPos> containers = findNearbyContainers(level, civ, farmer.blockPosition(), chestSearchRadius);
        List<ItemStack> leftovers = storeAll(containers, drops, level);

        for (ItemStack rem : leftovers) {
            if (!rem.isEmpty()) farmer.spawnAtLocation(rem);
        }
    }

    private void consumeOneSeedFromDrops(List<ItemStack> drops, Item seedItem) {
        for (ItemStack s : drops) {
            if (s != null && !s.isEmpty() && s.is(seedItem)) {
                s.shrink(1);
                return;
            }
        }
    }

    private boolean tryConsumeSeedFromChests(ServerLevel level, Civilization civ, Item seedItem) {
        List<BlockPos> containers = findNearbyContainers(level, civ, farmer.blockPosition(), chestSearchRadius);

        for (BlockPos pos : containers) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container c)) continue;

            ItemStack taken = InventoryUtil.extract(c, seedItem, 1);
            if (!taken.isEmpty()) return true;
        }

        return false;
    }

    private List<ItemStack> storeAll(List<BlockPos> containers, List<ItemStack> items, ServerLevel level) {
        List<ItemStack> remaining = new ArrayList<>();

        for (ItemStack stack : items) {
            if (stack == null || stack.isEmpty()) continue;

            ItemStack rem = stack.copy();

            for (BlockPos pos : containers) {
                BlockEntity be = level.getBlockEntity(pos);
                if (!(be instanceof Container c)) continue;

                rem = InventoryUtil.insert(c, rem);
                if (rem.isEmpty()) break;
            }

            if (!rem.isEmpty()) remaining.add(rem);
        }

        return remaining;
    }

    /* ---------------- Searches ---------------- */

    private BlockPos findMatureCrop(Civilization civ) {
        if (!(farmer.level() instanceof ServerLevel level)) return null;

        BlockPos center = farmer.blockPosition();

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -cropSearchRadius; dx <= cropSearchRadius; dx++) {
                for (int dz = -cropSearchRadius; dz <= cropSearchRadius; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    if (!CivTerritoryUtil.isInTerritory(civ, p)) continue;

                    BlockState st = level.getBlockState(p);
                    if (st.getBlock() instanceof CropBlock crop && crop.isMaxAge(st)) {
                        return p;
                    }
                }
            }
        }

        return null;
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
        if (!(farmer.level() instanceof ServerLevel level)) return null;
        if (farmer.getCivId() == null) return null;

        CivSavedData data = CivSavedData.get(level.getServer());
        return data.civs().get(farmer.getCivId());
    }
}
