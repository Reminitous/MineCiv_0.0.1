package net.reminitous.mineciv.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.reminitous.mineciv.block.ModBlocks;
import net.reminitous.mineciv.block.entity.MonumentBlockEntity;
import net.reminitous.mineciv.civ.CivilizationType;
import net.minecraft.world.entity.EquipmentSlot;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;

public class LumberjackWorkGoal extends Goal {

    private final Villager villager;

    private static final int SEARCH_RADIUS = 16;
    private static final int COOLDOWN_TICKS = 40;

    // Chop / deposit distances
    private static final int CHOP_DISTANCE_SQR = 3;
    private static final int CHEST_USE_DISTANCE_SQR = 4;

    // Performance / cadence
    private static final int MAX_LOG_SCAN_POSITIONS = 4096; // hard cap
    private static final int ITEM_SWEEP_INTERVAL_TICKS = 40; // every 2 seconds
    private static final int DEPOSIT_CHECK_INTERVAL_TICKS = 20; // every 1 second

    private int cooldown = 0;
    private int itemSweepTimer = 0;
    private int depositCheckTimer = 0;

    @Nullable private BlockPos monumentPos;
    @Nullable private BlockPos currentLogTarget;
    @Nullable private BlockPos currentChestTarget;

    private enum Mode { CHOP, DEPOSIT }
    private Mode mode = Mode.CHOP;

    public LumberjackWorkGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;

        // Only run for Lumberjacks
        if (villager.getVillagerData().getProfession() != ModVillagers.LUMBERJACK.get()) {
            cooldown = COOLDOWN_TICKS;
            return false;
        }

        monumentPos = findNearestLumberjackMonument(villager.blockPosition(), SEARCH_RADIUS);
        if (monumentPos == null) {
            cooldown = COOLDOWN_TICKS;
            return false;
        }

        // If we already have depositables and there is a chest, start depositing
        if (hasDepositables()) {
            BlockPos chest = findChestInMonumentChunk(monumentPos);
            if (chest != null) {
                currentChestTarget = chest;
                mode = Mode.DEPOSIT;
                return true;
            }
        }

        // Otherwise find a log to chop in the chunk
        currentLogTarget = findNearestLogInMonumentChunk(monumentPos, villager.blockPosition());
        if (currentLogTarget == null) {
            // Still allow the goal to run sometimes so it can sweep/collect items and deposit them
            // even if there are no logs right now.
            // But if there is nothing to do at all, cooldown out.
            if (!chunkHasAnyDepositableItems(monumentPos)) {
                cooldown = COOLDOWN_TICKS;
                return false;
            }
            // There are items to collect, so run; we'll sweep and then deposit.
            mode = Mode.CHOP;
            return true;
        }

        mode = Mode.CHOP;
        return true;
    }

    @Override
    public void start() {
        // reset timers so we sweep/deposit soon after starting
        itemSweepTimer = 0;
        depositCheckTimer = 0;

        if (mode == Mode.DEPOSIT && currentChestTarget != null) {
            moveTo(currentChestTarget);
        } else if (mode == Mode.CHOP && currentLogTarget != null) {
            moveTo(currentLogTarget);
        }
    }

    @Override
    public boolean canContinueToUse() {
        // Continue while monument exists; targets may be reacquired.
        return monumentPos != null;
    }

    @Override
    public void tick() {
        if (monumentPos == null) return;

        // Periodically sweep the entire chunk for tree drops & player drops (depositables)
        if (--itemSweepTimer <= 0) {
            itemSweepTimer = ITEM_SWEEP_INTERVAL_TICKS;
            if (villager.level() instanceof ServerLevel serverLevel) {
                sweepChunkItems(serverLevel, monumentPos);
            }
        }

        // Periodically check whether we should switch to deposit
        if (--depositCheckTimer <= 0) {
            depositCheckTimer = DEPOSIT_CHECK_INTERVAL_TICKS;

            if (hasDepositables()) {
                BlockPos chest = findChestInMonumentChunk(monumentPos);
                if (chest != null) {
                    currentChestTarget = chest;
                    mode = Mode.DEPOSIT;
                }
            } else if (mode == Mode.DEPOSIT) {
                // Nothing left to deposit; go back to chopping
                mode = Mode.CHOP;
                currentChestTarget = null;
            }
        }

        if (mode == Mode.DEPOSIT) {
            tickDeposit();
        } else {
            tickChop();
        }
    }

    private void tickChop() {
        Level level = villager.level();

        // Respect mobGriefing for block breaking
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            stopWithCooldown();
            return;
        }

        // If no current target, try to find one
        if (currentLogTarget == null) {
            currentLogTarget = findNearestLogInMonumentChunk(monumentPos, villager.blockPosition());
            // If still none, just idle a bit; we might still be collecting items from decaying leaves / player drops.
            if (currentLogTarget == null) return;
        }

        // If target got removed, reacquire
        if (!level.getBlockState(currentLogTarget).is(BlockTags.LOGS)) {
            currentLogTarget = findNearestLogInMonumentChunk(monumentPos, villager.blockPosition());
            if (currentLogTarget != null) moveTo(currentLogTarget);
            return;
        }

        // Navigate closer
        double dist = villager.distanceToSqr(
                currentLogTarget.getX() + 0.5,
                currentLogTarget.getY() + 0.5,
                currentLogTarget.getZ() + 0.5
        );
        if (dist > CHOP_DISTANCE_SQR) {
            moveTo(currentLogTarget);
            return;
        }

        // Choose a log to chop (bottom-most in its column) so trees fall naturally via continued chopping
        BlockPos chopPos = findLowestConnectedLogInColumn(currentLogTarget, level);
        if (chopPos == null) return;

        if (level instanceof ServerLevel serverLevel) {
            // Damage an axe if the villager has one, BUT do not require one.
            damageAnyAxeIfPresent();

            // Break exactly one log per tick (safe for performance)
            serverLevel.destroyBlock(chopPos, true, villager);

            // After breaking, vacuum nearby drops right around the chopped block (quick win),
            // plus the periodic chunk sweep handles far-away drops.
            suckUpNearbyItems(serverLevel, chopPos);

            // Clear target if it's not a log anymore, to force reacquire
            if (!level.getBlockState(chopPos).is(BlockTags.LOGS)) {
                currentLogTarget = null;
            }
        }
    }

    private void tickDeposit() {
        if (currentChestTarget == null) {
            currentChestTarget = findChestInMonumentChunk(monumentPos);
            if (currentChestTarget == null) return;
        }

        Level level = villager.level();
        BlockEntity be = level.getBlockEntity(currentChestTarget);
        if (be == null) {
            // Chest missing; try another
            currentChestTarget = findChestInMonumentChunk(monumentPos);
            return;
        }

        double dist = villager.distanceToSqr(
                currentChestTarget.getX() + 0.5,
                currentChestTarget.getY() + 0.5,
                currentChestTarget.getZ() + 0.5
        );
        if (dist > CHEST_USE_DISTANCE_SQR) {
            moveTo(currentChestTarget);
            return;
        }

        IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).orElse(null);
        if (handler == null) handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
        if (handler == null) return;

        depositAllDepositables(handler);

        // If we still have depositables, stay in deposit mode; otherwise go back to chopping
        if (!hasDepositables()) {
            mode = Mode.CHOP;
            currentChestTarget = null;
            currentLogTarget = null; // reacquire
        }
    }

    private void moveTo(BlockPos pos) {
        villager.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
    }

    private void stopWithCooldown() {
        villager.getNavigation().stop();
        monumentPos = null;
        currentLogTarget = null;
        currentChestTarget = null;
        cooldown = COOLDOWN_TICKS;
    }

    // =========================
    // Monument / Chunk Utilities
    // =========================

    @Nullable
    private BlockPos findNearestLumberjackMonument(BlockPos origin, int r) {
        Level level = villager.level();

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -2, -r), origin.offset(r, 2, r))) {
            if (level.getBlockState(pos).getBlock() != ModBlocks.MONUMENT_BLOCK.get()) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MonumentBlockEntity monumentBE)) continue;

            // TODO: Change this to CivilizationType.LUMBERJACK if/when you add it.
            if (monumentBE.getCivType() != CivilizationType.FARMER) continue;

            double d = pos.distSqr(origin);
            if (d < bestDist) {
                bestDist = d;
                best = pos.immutable();
            }
        }

        return best;
    }

    private int chunkX(BlockPos p) { return p.getX() >> 4; }
    private int chunkZ(BlockPos p) { return p.getZ() >> 4; }

    private int chunkMinX(BlockPos chunkAnchor) { return (chunkX(chunkAnchor) << 4); }
    private int chunkMinZ(BlockPos chunkAnchor) { return (chunkZ(chunkAnchor) << 4); }
    private int chunkMaxX(BlockPos chunkAnchor) { return chunkMinX(chunkAnchor) + 15; }
    private int chunkMaxZ(BlockPos chunkAnchor) { return chunkMinZ(chunkAnchor) + 15; }

    @Nullable
    private BlockPos findNearestLogInMonumentChunk(BlockPos monument, BlockPos origin) {
        Level level = villager.level();

        int minX = chunkMinX(monument);
        int minZ = chunkMinZ(monument);
        int maxX = chunkMaxX(monument);
        int maxZ = chunkMaxZ(monument);

        // Scan around origin Y band to reduce cost
        int minY = Math.max(level.getMinBuildHeight(), origin.getY() - 12);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, origin.getY() + 24);

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        int scanned = 0;
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (++scanned > MAX_LOG_SCAN_POSITIONS) return best;

                    BlockPos p = new BlockPos(x, y, z);
                    if (!level.getBlockState(p).is(BlockTags.LOGS)) continue;

                    // Optional guard: avoid chopping fully enclosed logs (likely player builds)
                    // Comment out if you want them to chop ANY logs, even in houses.
                    if (!hasAnyAirNeighbor(level, p)) continue;

                    double d = p.distSqr(origin);
                    if (d < bestDist) {
                        bestDist = d;
                        best = p.immutable();
                    }
                }
            }
        }
        return best;
    }

    @Nullable
    private BlockPos findChestInMonumentChunk(BlockPos monument) {
        Level level = villager.level();

        int minX = chunkMinX(monument);
        int minZ = chunkMinZ(monument);
        int maxX = chunkMaxX(monument);
        int maxZ = chunkMaxZ(monument);

        int minY = Math.max(level.getMinBuildHeight(), monument.getY() - 12);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, monument.getY() + 12);

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    var state = level.getBlockState(p);

                    if (state.getBlock() != Blocks.CHEST && state.getBlock() != Blocks.TRAPPED_CHEST) continue;

                    double d = p.distSqr(monument);
                    if (d < bestDist) {
                        bestDist = d;
                        best = p.immutable();
                    }
                }
            }
        }

        return best;
    }

    private boolean chunkHasAnyDepositableItems(BlockPos monument) {
        if (!(villager.level() instanceof ServerLevel serverLevel)) return false;

        int minX = chunkMinX(monument);
        int minZ = chunkMinZ(monument);
        int maxX = chunkMaxX(monument) + 1;
        int maxZ = chunkMaxZ(monument) + 1;

        AABB box = new AABB(
                minX, serverLevel.getMinBuildHeight(), minZ,
                maxX, serverLevel.getMaxBuildHeight(), maxZ
        );

        List<ItemEntity> items = serverLevel.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.getItem().isEmpty() && isDepositable(e.getItem()));

        return !items.isEmpty();
    }

    // =========================
    // Item sweep & pickup
    // =========================

    private void sweepChunkItems(ServerLevel level, BlockPos monument) {
        int minX = chunkMinX(monument);
        int minZ = chunkMinZ(monument);
        int maxX = chunkMaxX(monument) + 1;
        int maxZ = chunkMaxZ(monument) + 1;

        AABB box = new AABB(
                minX, level.getMinBuildHeight(), minZ,
                maxX, level.getMaxBuildHeight(), maxZ
        );

        // Collect only depositables (tree-related) from anywhere in the chunk
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.getItem().isEmpty() && isDepositable(e.getItem()));

        for (ItemEntity entity : items) {
            ItemStack stack = entity.getItem().copy();
            ItemStack remaining = addToVillagerInventory(stack);

            if (remaining.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(remaining);
            }
        }
    }

    private void suckUpNearbyItems(ServerLevel level, BlockPos center) {
        List<ItemEntity> items = level.getEntitiesOfClass(
                ItemEntity.class,
                new AABB(
                        center.getX() - 2, center.getY() - 2, center.getZ() - 2,
                        center.getX() + 3, center.getY() + 3, center.getZ() + 3
                ),
                e -> e.isAlive() && !e.getItem().isEmpty() && isDepositable(e.getItem())
        );

        for (ItemEntity entity : items) {
            ItemStack stack = entity.getItem().copy();
            ItemStack remaining = addToVillagerInventory(stack);

            if (remaining.isEmpty()) {
                entity.discard();
            } else {
                entity.setItem(remaining);
            }
        }
    }

    private ItemStack addToVillagerInventory(ItemStack stack) {
        var inv = villager.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            if (stack.isEmpty()) break;

            ItemStack slot = inv.getItem(i);
            if (slot.isEmpty()) {
                inv.setItem(i, stack);
                return ItemStack.EMPTY;
            } else if (ItemStack.isSameItemSameComponents(slot, stack)) {
                int max = Math.min(slot.getMaxStackSize(), inv.getMaxStackSize());
                int canMove = Math.min(stack.getCount(), max - slot.getCount());
                if (canMove > 0) {
                    slot.grow(canMove);
                    stack.shrink(canMove);
                    inv.setItem(i, slot);
                }
            }
        }
        return stack;
    }

    // =========================
    // Axe usage with durability
    // =========================

    private void damageAnyAxeIfPresent() {
        var inv = villager.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;

            // Works for vanilla + modded axes
            if (s.canPerformAction(net.minecraftforge.common.ToolActions.AXE_DIG)) {

                // Damage the axe by 1 durability
                s.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);

                // If it broke, clear the slot
                if (s.isEmpty() || s.getDamageValue() >= s.getMaxDamage()) {
                    inv.setItem(i, ItemStack.EMPTY);
                } else {
                    inv.setItem(i, s);
                }

                return; // damage only ONE axe per log break
            }
        }
    }


    // =========================
    // Deposit logic
    // =========================

    private boolean hasDepositables() {
        var inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && isDepositable(s)) return true;
        }
        return false;
    }

    private boolean isDepositable(ItemStack stack) {
        // "tree stuff" you asked for:
        // - logs
        // - saplings/seedlings
        // - sticks
        // - apples
        // Expand anytime.
        return stack.is(ItemTags.LOGS)
                || stack.is(ItemTags.SAPLINGS)
                || stack.is(Items.STICK)
                || stack.is(Items.APPLE);
    }

    private void depositAllDepositables(IItemHandler handler) {
        var inv = villager.getInventory();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || !isDepositable(s)) continue;

            ItemStack toInsert = s.copy();
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, toInsert, false);

            if (remainder.isEmpty()) {
                inv.setItem(i, ItemStack.EMPTY);
            } else if (remainder.getCount() != s.getCount()) {
                inv.setItem(i, remainder);
            }
        }
    }

    // =========================
    // Chop helpers
    // =========================

    private boolean hasAnyAirNeighbor(Level level, BlockPos p) {
        return level.getBlockState(p.north()).isAir()
                || level.getBlockState(p.south()).isAir()
                || level.getBlockState(p.east()).isAir()
                || level.getBlockState(p.west()).isAir()
                || level.getBlockState(p.above()).isAir()
                || level.getBlockState(p.below()).isAir();
    }

    @Nullable
    private BlockPos findLowestConnectedLogInColumn(BlockPos start, Level level) {
        if (!level.getBlockState(start).is(BlockTags.LOGS)) return null;

        BlockPos p = start;
        for (int i = 0; i < 16; i++) {
            BlockPos below = p.below();
            if (level.getBlockState(below).is(BlockTags.LOGS)) {
                p = below;
            } else {
                break;
            }
        }
        return level.getBlockState(p).is(BlockTags.LOGS) ? p : null;
    }
}
