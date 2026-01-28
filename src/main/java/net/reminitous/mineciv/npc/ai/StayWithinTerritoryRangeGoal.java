package net.reminitous.mineciv.npc.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.npc.MineCivNpcBase;
import net.reminitous.mineciv.util.CivTerritoryUtil;

import java.util.EnumSet;

public class StayWithinTerritoryRangeGoal extends Goal {

    private final MineCivNpcBase npc;
    private final double speed;
    private final int extraChunksAllowed;

    private int cooldown = 0;

    public StayWithinTerritoryRangeGoal(MineCivNpcBase npc, double speed, int extraChunksAllowed) {
        this.npc = npc;
        this.speed = speed;
        this.extraChunksAllowed = Math.max(0, extraChunksAllowed);
        this.setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(npc.level() instanceof ServerLevel level)) return false;
        if (npc.getCivId() == null || npc.getHomeMonument() == null) return false;

        if (cooldown-- > 0) return false;
        cooldown = 20; // check once/sec

        Civilization civ = CivSavedData.get(level.getServer()).civs().get(npc.getCivId());
        if (civ == null) return false;

        // If outside allowed range, force return toward monument (v1)
        return !CivTerritoryUtil.isInOrNearTerritory(civ, npc.blockPosition(), extraChunksAllowed);
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
}
