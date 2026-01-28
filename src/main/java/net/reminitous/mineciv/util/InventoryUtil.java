package net.reminitous.mineciv.util;

import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class InventoryUtil {

    private InventoryUtil() {}

    /** Insert stack into container. Returns remainder (may be empty). */
    public static ItemStack insert(Container c, ItemStack stack) {
        if (c == null || stack == null || stack.isEmpty()) return ItemStack.EMPTY;

        ItemStack remaining = stack.copy();

        // 1) Fill existing stacks
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack slot = c.getItem(i);
            if (slot.isEmpty()) continue;

            if (ItemStack.isSameItemSameComponents(slot, remaining) && slot.getCount() < slot.getMaxStackSize()) {
                int canMove = Math.min(remaining.getCount(), slot.getMaxStackSize() - slot.getCount());
                if (canMove > 0) {
                    slot.grow(canMove);
                    remaining.shrink(canMove);
                    c.setChanged();
                    if (remaining.isEmpty()) return ItemStack.EMPTY;
                }
            }
        }

        // 2) Put into empty slots
        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack slot = c.getItem(i);
            if (!slot.isEmpty()) continue;

            int toMove = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            ItemStack placed = remaining.copy();
            placed.setCount(toMove);

            c.setItem(i, placed);
            remaining.shrink(toMove);
            c.setChanged();

            if (remaining.isEmpty()) return ItemStack.EMPTY;
        }

        return remaining;
    }

    /**
     * Try to extract up to `amount` of an item from the container.
     * Returns extracted stack (may be empty).
     */
    public static ItemStack extract(Container c, Item item, int amount) {
        if (c == null || item == null || amount <= 0) return ItemStack.EMPTY;

        int remaining = amount;
        ItemStack out = ItemStack.EMPTY;

        for (int i = 0; i < c.getContainerSize(); i++) {
            ItemStack slot = c.getItem(i);
            if (slot.isEmpty() || !slot.is(item)) continue;

            int take = Math.min(remaining, slot.getCount());
            if (take <= 0) continue;

            if (out.isEmpty()) {
                out = slot.copy();
                out.setCount(take);
            } else {
                out.grow(take);
            }

            slot.shrink(take);
            if (slot.isEmpty()) c.setItem(i, ItemStack.EMPTY);
            c.setChanged();

            remaining -= take;
            if (remaining <= 0) break;
        }

        return out;
    }
}
