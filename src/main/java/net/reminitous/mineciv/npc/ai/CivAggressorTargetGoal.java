package net.reminitous.mineciv.npc.ai;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.npc.MineCivNpcBase;

public class CivAggressorTargetGoal extends NearestAttackableTargetGoal<Player> {

    public CivAggressorTargetGoal(Mob mob) {
        super(mob, Player.class, 10, true, false, candidate -> isAggressor(mob, candidate));
    }

    private static boolean isAggressor(Mob mob, LivingEntity candidate) {
        if (!(candidate instanceof Player player)) return false;
        if (!(mob.level() instanceof ServerLevel level)) return false;
        if (!(mob instanceof MineCivNpcBase base)) return false;
        if (base.getCivId() == null) return false;

        CivSavedData data = CivSavedData.get(level.getServer());
        long now = level.getGameTime();

        return data.isAggressor(base.getCivId(), player.getUUID(), now);
    }
}
