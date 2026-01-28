package net.reminitous.mineciv.npc.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.npc.MineCivEnchanterNpc;
import net.reminitous.mineciv.util.CivTerritoryUtil;
import net.reminitous.mineciv.util.InventoryUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class EnchanterGenerateGoal extends Goal {

    private final MineCivEnchanterNpc npc;
    private final double speed;
    private final int chestSearchRadius;

    private int cooldown = 0;

    public EnchanterGenerateGoal(MineCivEnchanterNpc npc, double speed, int chestSearchRadius) {
        this.npc = npc;
        this.speed = speed;
        this.chestSearchRadius = Math.max(8, chestSearchRadius);
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(npc.level() instanceof ServerLevel level)) return false;
        if (npc.getCivId() == null) return false;

        if (cooldown-- > 0) return false;

        Civilization civ = getCiv(level);
        if (civ == null) return false;

        return CivTerritoryUtil.isInTerritory(civ, npc.blockPosition());
    }

    @Override
    public void start() {
        if (npc.getHomeMonument() != null) {
            npc.getNavigation().moveTo(
                    npc.getHomeMonument().getX() + 0.5,
                    npc.getHomeMonument().getY(),
                    npc.getHomeMonument().getZ() + 0.5,
                    speed
            );
        }
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;
        Civilization civ = getCiv(level);
        if (civ == null) return;

        if (!CivTerritoryUtil.isInTerritory(civ, npc.blockPosition())) return;

        List<BlockPos> containers = findNearbyContainers(level, civ, npc.blockPosition(), chestSearchRadius);
        if (containers.isEmpty()) {
            cooldown = 20 * 10; // 10s
            return;
        }

        float roll = npc.getRandom().nextFloat();

        if (roll < 0.55f) {
            ItemStack book = makeRandomEnchantedBook(level);
            depositOrDrop(level, containers, book);
            cooldown = 20 * (12 + npc.getRandom().nextInt(10)); // 12–21s
        } else {
            int count = 1 + npc.getRandom().nextInt(3); // 1–3
            ItemStack bottles = new ItemStack(Items.EXPERIENCE_BOTTLE, count);
            depositOrDrop(level, containers, bottles);
            cooldown = 20 * (18 + npc.getRandom().nextInt(18)); // 18–35s
        }
    }

    /* ---------------- Generation ---------------- */

    private ItemStack makeRandomEnchantedBook(ServerLevel level) {
        var registry = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT);

        Holder<Enchantment> holder = registry.getRandom(npc.getRandom()).orElse(null);
        if (holder == null) return new ItemStack(Items.ENCHANTED_BOOK);

        Enchantment ench = holder.value();
        int max = Math.max(1, ench.getMaxLevel());

        int lvl;
        float r = npc.getRandom().nextFloat();
        if (r < 0.70f) lvl = 1;
        else if (r < 0.92f) lvl = Math.min(2, max);
        else lvl = Math.min(3, max);

        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);

        // ✅ 1.21.1: writes the correct enchantment component for enchanted books
        EnchantmentHelper.updateEnchantments(book, mutable -> mutable.set(holder, lvl));

        return book;
    }

    /* ---------------- Storage ---------------- */

    private void depositOrDrop(ServerLevel level, List<BlockPos> containers, ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;

        ItemStack rem = stack.copy();

        for (BlockPos pos : containers) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container c)) continue;

            rem = InventoryUtil.insert(c, rem);
            if (rem.isEmpty()) return;
        }

        if (!rem.isEmpty()) npc.spawnAtLocation(rem);
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

    private Civilization getCiv(ServerLevel level) {
        CivSavedData data = CivSavedData.get(level.getServer());
        return data.civs().get(npc.getCivId());
    }
}
