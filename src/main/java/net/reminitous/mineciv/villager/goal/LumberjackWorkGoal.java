package net.reminitous.mineciv.villager.goal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.reminitous.mineciv.block.ModBlocks;
import net.reminitous.mineciv.block.entity.MonumentBlockEntity;
import net.reminitous.mineciv.civ.CivilizationType;
import net.reminitous.mineciv.villager.ModVillagers;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LumberjackWorkGoal extends Goal {

    private final Villager villager;

    private static final int SEARCH_RADIUS = 16;
    private static final int COOLDOWN_TICKS = 40;

    private static final int CHOP_DISTANCE_SQR = 3;
    private static final int CHEST_USE_DISTANCE_SQR = 4;

    private static final int ITEM_SWEEP_INTERVAL_TICKS = 40;   // 2s
    private static final int DEPOSIT_CHECK_INTERVAL_TICKS = 20; // 1s

    // Tree-felling limits (prevents runaway on giant player builds)
    private static final int MAX_LOGS_PER_TREE = 128;

    private int cooldown = 0;
    private int itemSweepTimer = 0;
    private int depositCheckTimer = 0;

    @Nullable private BlockPos monumentPos;
    @Nullable private BlockPos currentChestTarget;

    private enum Mode { CHOP, DEPOSIT }
    private Mode mode = Mode.CHOP;

    // “Chop the whole tree” queue
    private final Deque<BlockPos> treeLogQueue = new ArrayDeque<>();

    // Timed chopping state
    @Nullable private BlockPos breakTarget;
    private int breakProgressTicks = 0;
    private int breakTicksNeeded = 0;

    public LumberjackWorkGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;

        if (villager.getVillagerData().getProfession() != ModVillagers.LUMBERJACK.get()) {
            cooldown = COOLDOWN_TICKS;
            return false;
        }

        monumentPos = findNearestLumberjackMonument(villager.blockPosition(), SEARCH_RADIUS);
        if (monumentPos == null) {
            cooldown = COOLDOWN_TICKS;
            return false;
        }

        // Start in deposit mode if we already have stuff and a chest exists
        if (hasDepositables()) {
            BlockPos chest = findChestInMonumentChunk(monumentPos);
            if (chest != null) {
                currentChestTarget = chest;
                mode = Mode.DEPOSIT;
                return true;
            }
        }

        mode = Mode.CHOP;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return monumentPos != null;
    }

    @Override
    public void start() {
        itemSweepTimer = 0;
        depositCheckTimer = 0;

        // On start, try to equip an axe if present
        equipBestAxeFromInventory();

        if (mode == Mode.DEPOSIT && currentChestTarget != null) {
            moveTo(currentChestTarget);
        }
    }

    @Override
    public void tick() {
        if (monumentPos == null) return;

        Level level = villager.level();

        // Respect mobGriefing for chopping logs
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            stopWithCooldown();
            return;
        }

        // Periodically sweep the chunk for drops (including axes so the villager can use them)
        if (--itemSweepTimer <= 0) {
            itemSweepTimer = ITEM_SWEEP_INTERVAL_TICKS;
            if (level instanceof ServerLevel serverLevel) {
                sweepChunkItems(serverLevel, monumentPos);
            }
        }

        // Periodically decide whether to deposit
        if (--depositCheckTimer <= 0) {
            depositCheckTimer = DEPOSIT_CHECK_INTERVAL_TICKS;

            // Re-equip best axe if we picked one up
            equipBestAxeFromInventory();

            if (hasDepositables()) {
                BlockPos chest = findChestInMonumentChunk(monumentPos);
                if (chest != null) {
                    currentChestTarget = chest;
                    mode = Mode.DEPOSIT;
                }
            } else if (mode == Mode.DEPOSIT) {
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

    // =========================
    // CHOP: whole tree + timed breaking
    // =========================

    private void tickChop() {
        Level level = villager.level();

        // If we’re not currently working a tree, find a new one and build a queue
        if (treeLogQueue.isEmpty()) {
            BlockPos startLog = findAnyLogInMonumentChunk(monumentPos);
            if (startLog == null) return;

            buildTreeQueueFrom(startLog, level);
            // If nothing queued (weird), bail out
            if (treeLogQueue.isEmpty()) return;
        }

        // If we’re not currently breaking a block, set next target
        if (breakTarget == null) {
            breakTarget = nextValidQueuedLog(level);
            if (breakTarget == null) {
                // queue exhausted
                treeLogQueue.clear();
                return;
            }
            breakProgressTicks = 0;
            breakTicksNeeded = computeTicksToBreak(level, breakTarget);

            // If the villager has an axe, damage it once per log (at start of the log)
            damageEquippedAxeIfPresent();
        }

        // Move close to the break target
        double dist = villager.distanceToSqr(
                breakTarget.getX() + 0.5,
                breakTarget.getY() + 0.5,
                breakTarget.getZ() + 0.5
        );
        if (dist > CHOP_DISTANCE_SQR) {
            moveTo(breakTarget);
            return;
        }

        // “Chopping” animation
        villager.swing(InteractionHand.MAIN_HAND);

        // Progress time
        breakProgressTicks++;

        // Done? Break it
        if (villager.level() instanceof ServerLevel serverLevel && breakProgressTicks >= breakTicksNeeded) {
            serverLevel.destroyBlock(breakTarget, true, villager);

            // Vacuum nearby depositables; chunk sweep handles far drops
            suckUpNearbyItems(serverLevel, breakTarget);

            // Reset to next log
            breakTarget = null;
        }
    }

    @Nullable
    private BlockPos nextValidQueuedLog(Level level) {
        while (!treeLogQueue.isEmpty()) {
            BlockPos p = treeLogQueue.pollFirst();
            if (p == null) continue;
            if (!isInSameChunk(p, monumentPos)) continue;
            if (level.getBlockState(p).is(BlockTags.LOGS)) return p;
        }
        return null;
    }

    private void buildTreeQueueFrom(BlockPos startLog, Level level) {
        treeLogQueue.clear();

        // Flood fill connected logs (6-direction) capped at MAX_LOGS_PER_TREE
        Deque<BlockPos> stack = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        stack.push(startLog);
        visited.add(startLog);

        while (!stack.isEmpty() && treeLogQueue.size() < MAX_LOGS_PER_TREE) {
            BlockPos p = stack.pop();
            if (!isInSameChunk(p, monumentPos)) continue;
            if (!level.getBlockState(p).is(BlockTags.LOGS)) continue;

            treeLogQueue.addLast(p.immutable());

            for (Direction dir : Direction.values()) {
                BlockPos n = p.relative(dir);
                if (!isInSameChunk(n, monumentPos)) continue;
                if (visited.add(n)) {
                    // Only explore neighbors that are logs; saves work
                    if (level.getBlockState(n).is(BlockTags.LOGS)) {
                        stack.push(n);
                    }
                }
            }
        }
    }

    /**
     * Approximate “player-like” chopping time:
     * - hardness matters (destroy speed)
     * - tool tier matters (ItemStack#getDestroySpeed)
     * - no axe => slow
     */
    private int computeTicksToBreak(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        // block hardness (destroy speed)
        float hardness = state.getDestroySpeed(level, pos);
        if (hardness < 0) return 20; // unbreakable-ish safety

        // tool speed
        ItemStack held = villager.getItemBySlot(EquipmentSlot.MAINHAND);
        float toolSpeed = 1.0f;

        if (!held.isEmpty() && held.canPerformAction(ToolActions.AXE_DIG)) {
            // This returns higher values for better axes on appropriate blocks
            toolSpeed = held.getDestroySpeed(state);
        }

        // If no axe, be slower than hand, but still possible
        if (held.isEmpty() || !held.canPerformAction(ToolActions.AXE_DIG)) {
            toolSpeed = 1.0f;
        }

        // Convert to ticks.
        // Tuned so: better axe -> fewer ticks; no axe -> noticeably slower.
        float effective = Math.max(1.0f, toolSpeed);
        int ticks = (int) Math.ceil((hardness * 30.0f) / effective);

        // Clamp so it never becomes instant or absurdly long
        if (ticks < 6) ticks = 6;
        if (ticks > 60) ticks = 60;

        // No axe penalty
        if (held.isEmpty() || !held.canPerformAction(ToolActions.AXE_DIG)) {
            ticks = Math.min(80, ticks * 2);
        }

        return ticks;
    }

    // =========================
    // DEPOSIT
    // =========================

    private void tickDeposit() {
        if (currentChestTarget == null) {
            currentChestTarget = findChestInMonumentChunk(monumentPos);
            if (currentChestTarget == null) {
                mode = Mode.CHOP;
                return;
            }
        }

        Level level = villager.level();
        BlockEntity be = level.getBlockEntity(currentChestTarget);
        if (be == null) {
            currentChestTarget = null;
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

        if (!hasDepositables()) {
            mode = Mode.CHOP;
            currentChestTarget = null;
        }
    }

    // =========================
    // Axe equipping + durability
    // =========================

    /**
     * Villagers won’t auto-equip tools you drop into their inventory.
     * This moves the “best” axe from villager inventory into MAINHAND.
     */
    private void equipBestAxeFromInventory() {
        var inv = villager.getInventory();

        int bestSlot = -1;
        float bestSpeed = 0.0f;

        // Measure against a log blockstate as reference
        BlockState referenceLog = Blocks.OAK_LOG.defaultBlockState();

        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;
            if (!s.canPerformAction(ToolActions.AXE_DIG)) continue;

            float speed = s.getDestroySpeed(referenceLog);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                bestSlot = i;
            }
        }

        ItemStack current = villager.getItemBySlot(EquipmentSlot.MAINHAND);

        // If already holding an axe, keep it (unless we found a better one)
        if (!current.isEmpty() && current.canPerformAction(ToolActions.AXE_DIG)) {
            float currentSpeed = current.getDestroySpeed(referenceLog);
            if (bestSlot == -1 || currentSpeed >= bestSpeed) return;
        }

        if (bestSlot == -1) {
            // No axe in inventory: keep whatever is in hand (or empty) and chop without tool
            return;
        }

        // Move the axe into mainhand (remove from inventory slot)
        ItemStack axe = inv.getItem(bestSlot);
        inv.setItem(bestSlot, ItemStack.EMPTY);

        // If holding something else, put it back into inventory
        if (!current.isEmpty()) {
            ItemStack leftover = addToVillagerInventory(current.copy());
            // If it can’t fit, just drop it
            if (!leftover.isEmpty() && villager.level() instanceof ServerLevel serverLevel) {
                serverLevel.addFreshEntity(new ItemEntity(serverLevel,
                        villager.getX(), villager.getY(), villager.getZ(), leftover));
            }
        }

        villager.setItemSlot(EquipmentSlot.MAINHAND, axe);
    }

    /**
     * Damage the equipped axe once per log.
     * Uses the overload your mappings support: hurtAndBreak(int, LivingEntity, EquipmentSlot)
     */
    private void damageEquippedAxeIfPresent() {
        ItemStack held = villager.getItemBySlot(EquipmentSlot.MAINHAND);
        if (held.isEmpty()) return;
        if (!held.canPerformAction(ToolActions.AXE_DIG)) return;

        held.hurtAndBreak(1, villager, EquipmentSlot.MAINHAND);

        if (held.isEmpty() || held.getDamageValue() >= held.getMaxDamage()) {
            villager.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        } else {
            villager.setItemSlot(EquipmentSlot.MAINHAND, held);
        }
    }

    // =========================
    // Chunk item sweep / pickup / deposit rules
    // =========================

    private void sweepChunkItems(ServerLevel level, BlockPos monument) {
        int minX = (monument.getX() >> 4) << 4;
        int minZ = (monument.getZ() >> 4) << 4;
        int maxX = minX + 16;
        int maxZ = minZ + 16;

        AABB box = new AABB(
                minX, level.getMinBuildHeight(), minZ,
                maxX, level.getMaxBuildHeight(), maxZ
        );

        // Pick up:
        // - depositables (logs/saplings/sticks/apples)
        // - axes (so the villager can equip them)
        List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box,
                e -> e.isAlive() && !e.getItem().isEmpty() && (isDepositable(e.getItem()) || isAxe(e.getItem())));

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

    private boolean hasDepositables() {
        var inv = villager.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && isDepositable(s)) return true;
        }
        return false;
    }

    private boolean isDepositable(ItemStack stack) {
        return stack.is(ItemTags.LOGS)
                || stack.is(ItemTags.SAPLINGS)
                || stack.is(Items.STICK)
                || stack.is(Items.APPLE);
    }

    private boolean isAxe(ItemStack stack) {
        return stack.canPerformAction(ToolActions.AXE_DIG);
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
    // Monument / chunk helpers
    // =========================

    private void moveTo(BlockPos pos) {
        villager.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 1.0);
    }

    private void stopWithCooldown() {
        villager.getNavigation().stop();
        monumentPos = null;
        currentChestTarget = null;
        treeLogQueue.clear();
        breakTarget = null;
        breakProgressTicks = 0;
        breakTicksNeeded = 0;
        cooldown = COOLDOWN_TICKS;
    }

    private boolean isInSameChunk(BlockPos a, BlockPos b) {
        return (a.getX() >> 4) == (b.getX() >> 4) && (a.getZ() >> 4) == (b.getZ() >> 4);
    }

    @Nullable
    private BlockPos findAnyLogInMonumentChunk(BlockPos monument) {
        Level level = villager.level();

        int minX = (monument.getX() >> 4) << 4;
        int minZ = (monument.getZ() >> 4) << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

        int minY = Math.max(level.getMinBuildHeight(), monument.getY() - 24);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, monument.getY() + 48);

        // Simple scan; “any log” is fine because we then flood-fill the tree
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos p = new BlockPos(x, y, z);
                    if (level.getBlockState(p).is(BlockTags.LOGS)) return p;
                }
            }
        }

        return null;
    }

    @Nullable
    private BlockPos findNearestLumberjackMonument(BlockPos origin, int r) {
        Level level = villager.level();

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(origin.offset(-r, -2, -r), origin.offset(r, 2, r))) {
            if (level.getBlockState(pos).getBlock() != ModBlocks.MONUMENT_BLOCK.get()) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MonumentBlockEntity monumentBE)) continue;

            // TODO: change to CivilizationType.LUMBERJACK when you add it
            if (monumentBE.getCivType() != CivilizationType.FARMER) continue;

            double d = pos.distSqr(origin);
            if (d < bestDist) {
                bestDist = d;
                best = pos.immutable();
            }
        }

        return best;
    }

    @Nullable
    private BlockPos findChestInMonumentChunk(BlockPos monument) {
        Level level = villager.level();

        int minX = (monument.getX() >> 4) << 4;
        int minZ = (monument.getZ() >> 4) << 4;
        int maxX = minX + 15;
        int maxZ = minZ + 15;

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
}
