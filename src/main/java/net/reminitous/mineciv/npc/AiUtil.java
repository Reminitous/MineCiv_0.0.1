package net.reminitous.mineciv.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public final class AiUtil {

    private static final Random RAND = new Random();

    public static Vec3 randomPointInRadius(MineCivNpcBase npc) {
        BlockPos center = npc.getMonument();
        int r = npc.getCivRadius() - 2;

        int dx = RAND.nextInt(-r, r);
        int dz = RAND.nextInt(-r, r);

        return Vec3.atCenterOf(center.offset(dx, 0, dz));
    }

    public static Vec3 pickBorderPoint(MineCivNpcBase npc) {
        BlockPos center = npc.getMonument();
        int r = npc.getCivRadius() - 1;

        int dx = RAND.nextBoolean() ? r : -r;
        int dz = RAND.nextInt(-r, r);

        return Vec3.atCenterOf(center.offset(dx, 0, dz));
    }

    public static Vec3 findNearbyAnimals(PathfinderMob npc, int radius) {
        var list = npc.level().getEntitiesOfClass(
                net.minecraft.world.entity.animal.Animal.class,
                npc.getBoundingBox().inflate(radius)
        );
        if (list.isEmpty()) return randomPointInRadius((MineCivNpcBase) npc);
        return list.get(RAND.nextInt(list.size())).position();
    }

    public static Vec3 findNearbyTrees(PathfinderMob npc, int radius) {
        BlockPos base = npc.blockPosition();
        for (int i = 0; i < 30; i++) {
            BlockPos p = base.offset(
                    RAND.nextInt(-radius, radius),
                    0,
                    RAND.nextInt(-radius, radius)
            );
            if (npc.level().getBlockState(p).is(net.minecraft.tags.BlockTags.LOGS)) {
                return Vec3.atCenterOf(p);
            }
        }
        return randomPointInRadius((MineCivNpcBase) npc);
    }
}
