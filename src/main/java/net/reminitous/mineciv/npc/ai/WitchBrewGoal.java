package net.reminitous.mineciv.npc.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.npc.MineCivWitchNpc;
import net.reminitous.mineciv.util.CivTerritoryUtil;
import net.reminitous.mineciv.util.InventoryUtil;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class WitchBrewGoal extends Goal {

    private final MineCivWitchNpc npc;
    private final double speed;
    private final int chestSearchRadius;

    private int cooldown = 0;

    public WitchBrewGoal(MineCivWitchNpc npc, double speed, int chestSearchRadius) {
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
        cooldown = 40; // every 2 seconds

        Civilization civ = getCiv(level);
        if (civ == null) return false;

        // Only work inside territory
        return CivTerritoryUtil.isInTerritory(civ, npc.blockPosition());
    }

    @Override
    public void start() {
        // v1: just stay near monument while doing “background work”
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
        if (containers.isEmpty()) return;

        // First try: brew from real ingredients
        if (tryBrewFromIngredients(level, containers)) {
            cooldown = 20 * 10; // 10 seconds
            return;
        }

        // Otherwise: occasional random conjure (reasonable randomness)
        if (npc.getRandom().nextInt(20) == 0) {
            conjureRandomPotion(level, containers);
            cooldown = 20 * 20; // 20 seconds after conjure
        }
    }

    /* ---------------- Brewing logic (v1) ---------------- */

    /**
     * v1 “brewing” = consume:
     * - 1x GLASS_BOTTLE
     * - 1x NETHER_WART
     * - 1x ingredient item
     * and create 1 potion item deposited to chests.
     */
    private boolean tryBrewFromIngredients(ServerLevel level, List<BlockPos> containers) {
        if (countInContainers(level, containers, Items.GLASS_BOTTLE) < 1) return false;
        if (countInContainers(level, containers, Items.NETHER_WART) < 1) return false;

        PotionRecipe recipe = findFirstRecipeAvailable(level, containers);
        if (recipe == null) return false;

        // Consume items
        if (!consumeFromContainers(level, containers, Items.GLASS_BOTTLE, 1)) return false;
        if (!consumeFromContainers(level, containers, Items.NETHER_WART, 1)) return false;
        if (!consumeFromContainers(level, containers, recipe.ingredient(), 1)) return false;

        // Create potion output (1.21+)
        ItemStack potion = PotionContents.createItemStack(Items.POTION, recipe.potion());

        // Deposit into chests
        ItemStack rem = insertIntoAny(level, containers, potion);
        if (!rem.isEmpty()) npc.spawnAtLocation(rem);

        return true;
    }

    private void conjureRandomPotion(ServerLevel level, List<BlockPos> containers) {
        PotionRecipe pick = ALL_RECIPES[npc.getRandom().nextInt(ALL_RECIPES.length)];

        ItemStack potion = PotionContents.createItemStack(Items.POTION, pick.potion());

        ItemStack rem = insertIntoAny(level, containers, potion);
        if (!rem.isEmpty()) npc.spawnAtLocation(rem);
    }

    /* ---------------- Recipes ---------------- */

    private record PotionRecipe(Item ingredient, Holder<Potion> potion) {}

    private static final PotionRecipe[] ALL_RECIPES = new PotionRecipe[] {
            new PotionRecipe(Items.SUGAR, Potions.SWIFTNESS),
            new PotionRecipe(Items.RABBIT_FOOT, Potions.LEAPING),
            new PotionRecipe(Items.BLAZE_POWDER, Potions.STRENGTH),
            new PotionRecipe(Items.MAGMA_CREAM, Potions.FIRE_RESISTANCE),
            new PotionRecipe(Items.GLISTERING_MELON_SLICE, Potions.HEALING),
            new PotionRecipe(Items.SPIDER_EYE, Potions.POISON),
            new PotionRecipe(Items.FERMENTED_SPIDER_EYE, Potions.WEAKNESS),
            new PotionRecipe(Items.GOLDEN_CARROT, Potions.NIGHT_VISION),
            new PotionRecipe(Items.GHAST_TEAR, Potions.REGENERATION)
    };

    private PotionRecipe findFirstRecipeAvailable(ServerLevel level, List<BlockPos> containers) {
        for (PotionRecipe r : ALL_RECIPES) {
            if (countInContainers(level, containers, r.ingredient()) > 0) return r;
        }
        return null;
    }

    /* ---------------- Container helpers ---------------- */

    private int countInContainers(ServerLevel level, List<BlockPos> containers, Item item) {
        int count = 0;
        for (BlockPos pos : containers) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container c)) continue;

            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && s.is(item)) count += s.getCount();
            }
        }
        return count;
    }

    private boolean consumeFromContainers(ServerLevel level, List<BlockPos> containers, Item item, int amount) {
        int remaining = amount;

        for (BlockPos pos : containers) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container c)) continue;

            ItemStack taken = InventoryUtil.extract(c, item, remaining);
            if (!taken.isEmpty()) {
                remaining -= taken.getCount();
                if (remaining <= 0) return true;
            }
        }

        return false;
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
