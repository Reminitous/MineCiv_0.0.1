package net.reminitous.mineciv.villager.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.reminitous.mineciv.villager.ModVillagers;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class CultivatorWorkGoal extends Goal {
    private final Villager villager;

    private int cooldown = 0;

    private enum Mode { HARVEST, DEPOSIT }
    private Mode mode = Mode.HARVEST;

    @Nullable private BlockPos targetCrop;
    @Nullable private BlockPos targetContainer;

    // Tweak these
    private static final int SEARCH_RADIUS = 10;
    private static final int DEPOSIT_RADIUS = 10;
    private static final int COOLDOWN_TICKS = 40;

    // What items we deposit
    private static final Item[] DEPOSIT_WHITELIST = new Item[] {
            net.minecraft.world.item.Items.WHEAT,
            net.minecraft.world.item.Items.CARROT,
            net.minecraft.world.item.Items.POTATO,
            net.minecraft.world.item.Items.BEETROOT,
            net.minecraft.world.item.Items.WHEAT_SEEDS,
            net.minecraft.world.item.Items.BEETROOT_SEEDS,
            net.minecraft.world.item.Items.MELON_SEEDS,
            net.minecraft.world.item.Items.PUMPKIN_SEEDS
    };

    public CultivatorWorkGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;

        // Only run for your profession
        if (villager.getVillagerData().getProfession() != ModVillagers.CULTIVATOR.get()) {
            cooldown = COOLDOWN_TICKS;
            return false;
        }

        // If you want to obey mobGriefing for harvesting/replanting:
        if (!villager.level().getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            cooldown = COOLDOWN_TICKS;
            return false;
        }

        // Decide whether to deposit
        if (shouldDeposit()) {
            mode = Mode.DEPOSIT;
            targetContainer = findNearestItemHandler(villager.blockPosition(), DEPOSIT_RADIUS);
            if (targetContainer != null) return true;
            // no chest found -> fall back to harvesting
            mode = Mode.HARVEST;
        }

        mode = Mode.HARVEST;
        targetCrop = findMatureCrop(villager.blockPosition(), SEARCH_RADIUS);
        if (targetCrop == null) {
            cooldown = COOLDOWN_TICKS;
            return false;
        }
        return true;
    }

    @Override
    public void start() {
        if (mode == Mode.HARVEST && targetCrop != null) {
            villager.getNavigation().moveTo(targetCrop.getX() + 0.5, targetCrop.getY(), targetCrop.getZ() + 0.5, 1.0);
        } else if (mode == Mode.DEPOSIT && targetContainer != null) {
            villager.getNavigation().moveTo(targetContainer.getX() + 0.5, targetContainer.getY(), targetContainer.getZ() + 0.5, 1.0);
        }
    }

    @Override
    public boolean canContinueToUse() {
        return (mode == Mode.HARVEST && targetCrop != null)
                || (mode == Mode.DEPOSIT && targetContainer != null);
    }

    @Override
    public void tick() {
        if (mode == Mode.HARVEST && targetCrop != null) {
            if (villager.distanceToSqr(targetCrop.getX() + 0.5, targetCrop.getY(), targetCrop.getZ() + 0.5) < 2.5) {
                harvestAndReplant(targetCrop);
                targetCrop = null;
                cooldown = COOLDOWN_TICKS;
            }
            return;
        }

        if (mode == Mode.DEPOSIT && targetContainer != null) {
            if (villager.distanceToSqr(targetContainer.getX() + 0.5, targetContainer.getY(), targetContainer.getZ() + 0.5) < 3.0) {
                depositWhitelistToContainer(targetContainer);
                targetContainer = null;
                cooldown = COOLDOWN_TICKS;
            }
        }
    }

    // -------------------------
    // Harvest / Replant
    // -------------------------

    private void harvestAndReplant(BlockPos pos) {
        Level level = villager.level();
        BlockState state = level.getBlockState(pos);

        if (!(state.getBlock() instanceof CropBlock crop)) return;
        if (!crop.isMaxAge(state)) return;

        // Determine the "seed" item for replant
        Item seed = crop.getCloneItemStack(level, pos, state).getItem();

        // Harvest (drops items)
        level.destroyBlock(pos, true, villager);

        // Replant if villager has seed item
        if (removeOneFromInventory(seed)) {
            level.setBlock(pos, crop.defaultBlockState(), 3);
        }
    }

    private boolean removeOneFromInventory(Item item) {
        var inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(item)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private BlockPos findMatureCrop(BlockPos origin, int r) {
        Level level = villager.level();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -1, -r), origin.offset(r, 1, r))) {
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof CropBlock crop)) continue;
            if (!crop.isMaxAge(state)) continue;

            double d = pos.distSqr(origin);
            if (d < bestDist) {
                bestDist = d;
                best = pos.immutable();
            }
        }
        return best;
    }

    // -------------------------
    // Deposit
    // -------------------------

    private boolean shouldDeposit() {
        // Simple rule: if carrying >= 16 of any deposit item
        int count = 0;
        var inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (isDepositItem(s.getItem())) count += s.getCount();
        }
        return count >= 16;
    }

    private boolean isDepositItem(Item item) {
        for (Item it : DEPOSIT_WHITELIST) {
            if (item == it) return true;
        }
        return false;
    }

    private void depositWhitelistToContainer(BlockPos containerPos) {
        BlockEntity be = villager.level().getBlockEntity(containerPos);
        if (be == null) return;

        var cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        if (!cap.isPresent()) return;

        IItemHandler handler = cap.orElseThrow(() -> new IllegalStateException("Missing ITEM_HANDLER capability"));
        var inv = villager.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            if (!isDepositItem(stack.getItem())) continue;

            ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, stack.copy(), false);
            int inserted = stack.getCount() - remainder.getCount();
            if (inserted > 0) stack.shrink(inserted);
        }

        be.setChanged();
    }

    @Nullable
    private BlockPos findNearestItemHandler(BlockPos origin, int r) {
        Level level = villager.level();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -1, -r), origin.offset(r, 1, r))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) continue;
            if (!be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent()) continue;

            double d = pos.distSqr(origin);
            if (d < bestDist) {
                bestDist = d;
                best = pos.immutable();
            }
        }
        return best;
    }
}
