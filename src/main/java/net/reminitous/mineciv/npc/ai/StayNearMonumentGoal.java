package net.reminitous.mineciv.npc.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.reminitous.mineciv.npc.MineCivNpcBase;

import java.util.EnumSet;

public class StayNearMonumentGoal extends Goal {

    private final Mob mob;
    private final double speed;
    private final int maxDistance;
    private final int checkIntervalTicks;

    private int cooldown;

    public StayNearMonumentGoal(Mob mob, double speed, int maxDistance, int checkIntervalTicks) {
        this.mob = mob;
        this.speed = speed;
        this.maxDistance = Math.max(4, maxDistance);
        this.checkIntervalTicks = Math.max(10, checkIntervalTicks);
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(mob instanceof MineCivNpcBase base)) return false;
        BlockPos home = base.homeMonument;
        if (home == null) return false;

        if (cooldown-- > 0) return false;
        cooldown = checkIntervalTicks;

        return mob.blockPosition().distManhattan(home) > maxDistance;
    }

    @Override
    public void start() {
        if (!(mob instanceof MineCivNpcBase base)) return;
        BlockPos home = base.homeMonument;
        if (home == null) return;

        // Move to a random point closer to home (not necessarily exactly on it)
        Vec3 from = mob.position();
        Vec3 to = Vec3.atCenterOf(home);

        Vec3 dir = to.subtract(from);
        if (dir.lengthSqr() < 1e-6) return;

        Vec3 step = dir.normalize().scale(Mth.clamp(dir.length() - (maxDistance * 0.6), 2.0, 12.0));
        Vec3 target = from.add(step);

        mob.getNavigation().moveTo(target.x, target.y, target.z, speed);
    }

    @Override
    public boolean canContinueToUse() {
        return !mob.getNavigation().isDone();
    }
}
