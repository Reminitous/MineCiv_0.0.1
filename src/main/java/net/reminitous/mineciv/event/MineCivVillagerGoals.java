package net.reminitous.mineciv.event;

import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.villager.CultivatorWorkGoal;
import net.reminitous.mineciv.villager.LumberjackWorkGoal;
import net.reminitous.mineciv.villager.MonumentCivConversionGoal;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public class MineCivVillagerGoals {

    private static final String KEY_GOALS_ADDED = "mineciv_goals_added";

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) return;

        if (!(event.getEntity() instanceof Villager villager)) return;

        // Prevent adding duplicate goals on chunk reload / rejoin
        var data = villager.getPersistentData();
        if (data.getBoolean(KEY_GOALS_ADDED)) return;
        data.putBoolean(KEY_GOALS_ADDED, true);

        /*
         * Add ALL MineCiv goals once.
         * Their canUse() methods decide when they actually run.
         *
         * Priorities: lower number = higher priority.
         * - Conversion should be higher priority than work so villagers become specialized first.
         */
        villager.goalSelector.addGoal(1, new MonumentCivConversionGoal(villager));
        villager.goalSelector.addGoal(3, new CultivatorWorkGoal(villager));
        villager.goalSelector.addGoal(3, new LumberjackWorkGoal(villager));
    }
}
