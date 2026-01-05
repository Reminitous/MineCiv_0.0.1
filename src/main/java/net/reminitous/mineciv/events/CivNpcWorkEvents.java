package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.InvWrapper;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.*;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class CivNpcWorkEvents {

    private CivNpcWorkEvents() {}

    // Work cadence
    private static final int WORK_INTERVAL_TICKS = 40; // every 2 seconds
    private static final int SCAN_RADIUS = 8;

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.level instanceof ServerLevel level)) return;

        if ((level.getServer().getTickCount() % WORK_INTERVAL_TICKS) != 0) return;

        CivSavedData data = CivSavedData.get(level.getServer());

        for (Civilization civ : data.civs().values()) {
            BlockPos monument = civ.monumentPos();
            if (monument == null) continue;

            String dim = civ.monumentDimId();
            if (dim == null || !dim.equals(level.dimension().location().toString())) continue;

            if (!level.hasChunkAt(monument)) continue;

            for (UUID npcId : civ.npcIds()) {
                Entity ent = level.getEntity(npcId);
                if (!(ent instanceof LivingEntity le) || !le.isAlive()) continue;

                // Only Villagers do work in this v0 (golems are guards)
                if (!(le instanceof Villager villager)) continue;

                String role = villager.getPersistentData().getString("MineCivRole");
                if (role == null) role = "";

                // Find a chest/barrel/shulker etc. nearby to deposit into
                IItemHandler storage = findNearestStorage(level, villager.blockPosition(), SCAN_RADIUS);

                if ("Farmer".equals(role) || "Shepherd".equals(role)) {
                    doFarmerWork(level, villager, storage);
                } else if ("Lumberjack".equals(role)) {
                    doLumberWork(level, villager, storage);
                } else if ("Factory Worker".equals(role) || "Engineer".equals(role)) {
                    doFactoryWork(level, villager, storage);
                }
            }
        }
    }

    /* ---------------- Farmer: harvest mature crops + deposit ---------------- */

    private static void doFarmerWork(ServerLevel level, Villager v, IItemHandler storage) {
        BlockPos origin = v.blockPosition();

        BlockPos target = findNearestMatureCrop(level, origin, 6);
        if (target == null) return;

        BlockState state = level.getBlockState(target);
        if (!(state.getBlock() instanceof CropBlock crop)) return;
        if (!crop.isMaxAge(state)) return;

        // Break crop (server-side) and capture drops
        List<ItemStack> drops = Block.getDrops(state, level, target, level.getBlockEntity(target), v, ItemStack.EMPTY);
        level.removeBlock(target, false);

        // Replant if the crop block supports it: we just place the same crop at age 0 (simple)
        // Works for most vanilla crops because the crop block itself is the plant.
        try {
            level.setBlock(target, crop.defaultBlockState(), 3);
        } catch (Exception ignored) {}

        depositOrDrop(level, target, storage, drops);
    }

    private static BlockPos findNearestMatureCrop(ServerLevel level, BlockPos origin, int radius) {
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos p = origin.offset(dx, 0, dz);
                if (!level.hasChunkAt(p)) continue;

                // Check a small vertical window (crops are typically y..y+2)
                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos q = p.offset(0, dy, 0);
                    BlockState st = level.getBlockState(q);
                    if (st.getBlock() instanceof CropBlock crop && crop.isMaxAge(st)) {
                        int dist = dx * dx + dz * dz + dy * dy;
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = q.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    /* ---------------- Lumberjack: break logs + deposit ---------------- */

    private static void doLumberWork(ServerLevel level, Villager v, IItemHandler storage) {
        BlockPos origin = v.blockPosition();

        BlockPos logPos = findNearestLog(level, origin, 7);
        if (logPos == null) return;

        BlockState state = level.getBlockState(logPos);
        Block b = state.getBlock();

        // Break one log and deposit
        List<ItemStack> drops = Block.getDrops(state, level, logPos, level.getBlockEntity(logPos), v, ItemStack.EMPTY);
        level.removeBlock(logPos, false);

        depositOrDrop(level, logPos, storage, drops);
    }

    private static BlockPos findNearestLog(ServerLevel level, BlockPos origin, int radius) {
        BlockPos best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos p = origin.offset(dx, 0, dz);
                if (!level.hasChunkAt(p)) continue;

                for (int dy = -3; dy <= 6; dy++) {
                    BlockPos q = p.offset(0, dy, 0);
                    BlockState st = level.getBlockState(q);
                    if (st.is(net.minecraft.tags.BlockTags.LOGS)) {
                        int dist = dx * dx + dz * dz + dy * dy;
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = q.immutable();
                        }
                    }
                }
            }
        }
        return best;
    }

    /* ---------------- Factory Worker: fuel furnaces + smelt + deposit ---------------- */

    private static void doFactoryWork(ServerLevel level, Villager v, IItemHandler storage) {
        BlockPos origin = v.blockPosition();

        AbstractFurnaceBlockEntity furnace = findNearestFurnace(level, origin, 8);
        if (furnace == null) return;

        // Ensure fuel (unlimited coal behavior)
        ensureFuel(furnace);

        // Push output to storage first (keeps furnace clear)
        pushFurnaceOutputToStorage(furnace, storage);

        // Pull a smeltable from storage into furnace input
        if (storage != null) {
            feedSmeltableFromStorage(level, furnace, storage);
        }
    }

    private static AbstractFurnaceBlockEntity findNearestFurnace(ServerLevel level, BlockPos origin, int radius) {
        AbstractFurnaceBlockEntity best = null;
        int bestDist = Integer.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos p = origin.offset(dx, 0, dz);
                if (!level.hasChunkAt(p)) continue;

                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos q = p.offset(0, dy, 0);
                    BlockEntity be = level.getBlockEntity(q);
                    if (be instanceof AbstractFurnaceBlockEntity f) {
                        int dist = dx * dx + dz * dz + dy * dy;
                        if (dist < bestDist) {
                            bestDist = dist;
                            best = f;
                        }
                    }
                }
            }
        }
        return best;
    }

    // Slot order in AbstractFurnace: 0=input, 1=fuel, 2=output
    private static void ensureFuel(AbstractFurnaceBlockEntity furnace) {
        ItemStack fuel = furnace.getItem(1);
        if (fuel.isEmpty() || fuel.getCount() < 16) {
            furnace.setItem(1, new ItemStack(Items.COAL, 64));
            furnace.setChanged();
        }
    }

    private static void pushFurnaceOutputToStorage(AbstractFurnaceBlockEntity furnace, IItemHandler storage) {
        if (storage == null) return;

        ItemStack out = furnace.getItem(2);
        if (out.isEmpty()) return;

        ItemStack remaining = insertAll(storage, out.copy());
        int inserted = out.getCount() - remaining.getCount();
        if (inserted > 0) {
            out.shrink(inserted);
            furnace.setItem(2, out);
            furnace.setChanged();
        }
    }

    private static void feedSmeltableFromStorage(ServerLevel level, AbstractFurnaceBlockEntity furnace, IItemHandler storage) {
        ItemStack input = furnace.getItem(0);
        if (!input.isEmpty() && input.getCount() >= input.getMaxStackSize()) return;

        for (int slot = 0; slot < storage.getSlots(); slot++) {
            ItemStack s = storage.getStackInSlot(slot);
            if (s.isEmpty()) continue;

            // Check if smeltable by furnace recipes
            if (!isSmeltable(level, s)) continue;

            ItemStack extracted = storage.extractItem(slot, 1, false);
            if (extracted.isEmpty()) continue;

            ItemStack current = furnace.getItem(0);
            if (current.isEmpty()) {
                furnace.setItem(0, extracted);
            } else if (ItemStack.isSameItemSameComponents(current, extracted)) {
                current.grow(1);
                furnace.setItem(0, current);
            } else {
                // can't insert, put back
                insertAll(storage, extracted);
            }

            furnace.setChanged();
            return; // one item per cycle
        }
    }

    private static boolean isSmeltable(ServerLevel level, ItemStack stack) {
        net.minecraft.world.item.crafting.SingleRecipeInput input =
                new net.minecraft.world.item.crafting.SingleRecipeInput(stack.copyWithCount(1));
        return level.getRecipeManager()
                .getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, input, level)
                .isPresent();
    }

    /* ---------------- Storage helpers ---------------- */

    private static IItemHandler findNearestStorage(ServerLevel level, BlockPos origin, int radius) {
        BlockEntity bestBe = null;
        int bestDist = Integer.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos p = origin.offset(dx, 0, dz);
                if (!level.hasChunkAt(p)) continue;

                for (int dy = -2; dy <= 2; dy++) {
                    BlockPos q = p.offset(0, dy, 0);
                    BlockEntity be = level.getBlockEntity(q);
                    if (be == null) continue;

                    // Any BE with item handler capability counts (chest, barrel, shulker, etc.)
                    var cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
                    if (!cap.isPresent()) continue;

                    int dist = dx * dx + dz * dz + dy * dy;
                    if (dist < bestDist) {
                        bestDist = dist;
                        bestBe = be;
                    }
                }
            }
        }

        if (bestBe == null) return null;
        return bestBe.getCapability(ForgeCapabilities.ITEM_HANDLER, null).orElse(null);
    }

    private static ItemStack insertAll(IItemHandler handler, ItemStack stack) {
        if (handler == null || stack.isEmpty()) return stack;

        ItemStack remaining = stack;
        for (int i = 0; i < handler.getSlots(); i++) {
            remaining = handler.insertItem(i, remaining, false);
            if (remaining.isEmpty()) return ItemStack.EMPTY;
        }
        return remaining;
    }

    private static void depositOrDrop(ServerLevel level, BlockPos at, IItemHandler storage, List<ItemStack> drops) {
        for (ItemStack s : drops) {
            if (s.isEmpty()) continue;

            if (storage != null) {
                ItemStack remaining = insertAll(storage, s.copy());
                if (!remaining.isEmpty()) {
                    Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, remaining);
                }
            } else {
                Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5, s);
            }
        }
    }
}
