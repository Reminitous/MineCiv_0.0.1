package net.reminitous.mineciv.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.reminitous.mineciv.block.ModBlocks;
import net.reminitous.mineciv.block.entity.MonumentBlockEntity;
import net.reminitous.mineciv.civ.CivilizationType;

import javax.annotation.Nullable;
import java.util.EnumSet;

public class MonumentCivConversionGoal extends Goal {

    private final Villager villager;

    private int cooldown = 0;

    @Nullable
    private BlockPos targetMonument;

    private static final int SEARCH_RADIUS = 16;
    private static final int COOLDOWN_TICKS = 40;

    public MonumentCivConversionGoal(Villager villager) {
        this.villager = villager;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (cooldown-- > 0) return false;

        // Only convert unemployed villagers
        if (villager.getVillagerData().getProfession() != VillagerProfession.NONE) {
            cooldown = COOLDOWN_TICKS;
            return false;
        }

        targetMonument = findNearestFarmerMonument(villager.blockPosition(), SEARCH_RADIUS);
        if (targetMonument == null) {
            cooldown = COOLDOWN_TICKS;
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        if (targetMonument != null) {
            villager.getNavigation().moveTo(
                    targetMonument.getX() + 0.5,
                    targetMonument.getY(),
                    targetMonument.getZ() + 0.5,
                    1.0
            );
        }
    }

    @Override
    public boolean canContinueToUse() {
        return targetMonument != null;
    }

    @Override
    public void tick() {
        if (targetMonument == null) return;

        double dist = villager.distanceToSqr(
                targetMonument.getX() + 0.5,
                targetMonument.getY(),
                targetMonument.getZ() + 0.5
        );

        // Close enough -> convert
        if (dist < 3.0) {
            int roll = villager.getRandom().nextInt(100);

            VillagerProfession chosen =
                    (roll < 70)
                            ? ModVillagers.CULTIVATOR.get()
                            : ModVillagers.LUMBERJACK.get();

            villager.setVillagerData(
                    villager.getVillagerData().setProfession(chosen)
            );

            // Refresh AI on server only (required by your mappings)
            if (villager.level() instanceof ServerLevel serverLevel) {
                villager.refreshBrain(serverLevel);
            }

            villager.getNavigation().stop();

            targetMonument = null;
            cooldown = COOLDOWN_TICKS;
        }
    }

    @Nullable
    private BlockPos findNearestFarmerMonument(BlockPos origin, int r) {
        Level level = villager.level();

        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.betweenClosed(
                origin.offset(-r, -2, -r),
                origin.offset(r, 2, r)
        )) {
            if (level.getBlockState(pos).getBlock() != ModBlocks.MONUMENT_BLOCK.get()) continue;

            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof MonumentBlockEntity monumentBE)) continue;

            if (monumentBE.getCivType() != CivilizationType.FARMER) continue;

            double d = pos.distSqr(origin);
            if (d < bestDist) {
                bestDist = d;
                best = pos.immutable();
            }
        }

        return best;
    }
}
