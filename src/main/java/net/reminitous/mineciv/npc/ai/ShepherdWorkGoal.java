package net.reminitous.mineciv.npc.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.npc.MineCivShepherdNpc;
import net.reminitous.mineciv.util.CivTerritoryUtil;
import net.reminitous.mineciv.util.InventoryUtil;

import java.util.*;

public class ShepherdWorkGoal extends Goal {

    private final MineCivShepherdNpc npc;
    private final double speed;
    private final int animalSearchRadius;
    private final int chestSearchRadius;

    private int cooldown = 0;

    private Animal mateA;
    private Animal mateB;
    private Item breedingItem;

    public ShepherdWorkGoal(MineCivShepherdNpc npc, double speed, int animalSearchRadius, int chestSearchRadius) {
        this.npc = npc;
        this.speed = speed;
        this.animalSearchRadius = Math.max(8, animalSearchRadius);
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

        // Shepherd must be in territory to start working
        if (!CivTerritoryUtil.isInTerritory(civ, npc.blockPosition())) return false;

        // Find a pair + food
        PairChoice choice = findPairAndFood(level, civ);
        if (choice == null) return false;

        this.mateA = choice.a;
        this.mateB = choice.b;
        this.breedingItem = choice.foodItem;

        return true;
    }

    @Override
    public void start() {
        if (mateA != null) {
            npc.getNavigation().moveTo(mateA, speed);
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (!(npc.level() instanceof ServerLevel)) return false;
        if (mateA == null || mateB == null || breedingItem == null) return false;
        if (!mateA.isAlive() || !mateB.isAlive()) return false;

        // Stop if they wander too far from each other
        return mateA.distanceToSqr(mateB) < (20.0 * 20.0);
    }

    @Override
    public void tick() {
        if (!(npc.level() instanceof ServerLevel level)) return;
        Civilization civ = getCiv(level);
        if (civ == null) {
            resetState();
            return;
        }

        if (mateA == null || mateB == null || breedingItem == null) {
            resetState();
            return;
        }

        // Ensure both are in territory (v1 rule: breed inside)
        if (!CivTerritoryUtil.isInTerritory(civ, mateA.blockPosition()) ||
                !CivTerritoryUtil.isInTerritory(civ, mateB.blockPosition())) {
            resetState();
            return;
        }

        // Walk towards midpoint
        BlockPos mid = midpoint(mateA.blockPosition(), mateB.blockPosition());
        npc.getNavigation().moveTo(mid.getX() + 0.5, mid.getY(), mid.getZ() + 0.5, speed);

        double d2 = mateA.distanceToSqr(mateB);
        if (d2 > 3.0 * 3.0) return;

        // Close enough: try to consume 2 food items (one per parent) from chests
        if (!consumeBreedingFood(level, civ, breedingItem, 2)) {
            resetState();
            return;
        }

        // Apply "in love" and spawn baby like vanilla does
        tryBreed(level, mateA, mateB);

        // Longer cooldown after a successful breed to avoid spam
        cooldown = 20 * 20; // 20 seconds
        resetState();
    }

    /* ---------------- Core helpers ---------------- */

    private void tryBreed(ServerLevel level, Animal a, Animal b) {
        // Only breed adults and not already in love
        if (a.isBaby() || b.isBaby()) return;
        if (a.getAge() != 0 || b.getAge() != 0) return;

        // Spawn baby
        AgeableMob child = a.getBreedOffspring(level, b);
        if (child == null) return;

        BlockPos pos = a.blockPosition();
        child.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360F, 0F);
        child.setAge(-24000); // baby

        level.addFreshEntity(child);

        // Put parents on cooldown (simple)
        a.setAge(6000);
        b.setAge(6000);
    }

    private boolean consumeBreedingFood(ServerLevel level, Civilization civ, Item item, int amount) {
        List<BlockPos> containers = findNearbyContainers(level, civ, npc.blockPosition(), chestSearchRadius);
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

    private static BlockPos midpoint(BlockPos a, BlockPos b) {
        return new BlockPos(
                (a.getX() + b.getX()) / 2,
                (a.getY() + b.getY()) / 2,
                (a.getZ() + b.getZ()) / 2
        );
    }

    private void resetState() {
        mateA = null;
        mateB = null;
        breedingItem = null;
    }

    /* ---------------- Pair selection ---------------- */

    private record PairChoice(Animal a, Animal b, Item foodItem) {}

    private PairChoice findPairAndFood(ServerLevel level, Civilization civ) {
        BlockPos center = npc.blockPosition();

        AABB box = new AABB(
                center.getX() - animalSearchRadius, center.getY() - 6, center.getZ() - animalSearchRadius,
                center.getX() + animalSearchRadius, center.getY() + 6, center.getZ() + animalSearchRadius
        );

        List<Animal> animals = level.getEntitiesOfClass(Animal.class, box, a -> {
            if (a == null || !a.isAlive()) return false;
            if (a.isBaby()) return false;
            if (!CivTerritoryUtil.isInTerritory(civ, a.blockPosition())) return false;

            // v1 allowed list:
            return (a instanceof Cow) || (a instanceof Sheep) || (a instanceof Pig) || (a instanceof Chicken);
        });

        if (animals.size() < 2) return null;

        // Group by type, find two of same type
        Map<Class<?>, List<Animal>> byType = new HashMap<>();
        for (Animal a : animals) {
            byType.computeIfAbsent(a.getClass(), k -> new ArrayList<>()).add(a);
        }

        for (var entry : byType.entrySet()) {
            List<Animal> list = entry.getValue();
            if (list.size() < 2) continue;

            Animal a = list.get(0);
            Animal b = list.get(1);

            Item food = breedingFoodFor(a);
            if (food == null) continue;

            // Must have at least 2 food in chests
            if (countFoodInChests(level, civ, food) >= 2) {
                return new PairChoice(a, b, food);
            }
        }

        return null;
    }

    private int countFoodInChests(ServerLevel level, Civilization civ, Item item) {
        List<BlockPos> containers = findNearbyContainers(level, civ, npc.blockPosition(), chestSearchRadius);
        int count = 0;

        for (BlockPos pos : containers) {
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof Container c)) continue;

            for (int i = 0; i < c.getContainerSize(); i++) {
                ItemStack s = c.getItem(i);
                if (!s.isEmpty() && s.is(item)) {
                    count += s.getCount();
                }
            }
        }

        return count;
    }

    private Item breedingFoodFor(Animal a) {
        // v1: use vanilla common foods
        if (a instanceof Cow) return Items.WHEAT;
        if (a instanceof Sheep) return Items.WHEAT;
        if (a instanceof Pig) return Items.CARROT;
        if (a instanceof Chicken) return Items.WHEAT_SEEDS;
        return null;
    }

    private Civilization getCiv(ServerLevel level) {
        CivSavedData data = CivSavedData.get(level.getServer());
        return data.civs().get(npc.getCivId());
    }
}
