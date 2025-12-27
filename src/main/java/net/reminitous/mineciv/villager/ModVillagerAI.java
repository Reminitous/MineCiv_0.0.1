package net.reminitous.mineciv.villager;

import net.minecraft.world.entity.npc.Villager;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = "mineciv")
public class ModVillagerAI {
    private static final String CULTIVATOR_GOAL_ADDED = "mineciv_cultivator_goal_added";

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Villager villager)) return;
        if (villager.level().isClientSide()) return;

        // Only add once per villager instance
        if (villager.getPersistentData().getBoolean(CULTIVATOR_GOAL_ADDED)) return;

        villager.goalSelector.addGoal(3, new CultivatorWorkGoal(villager));
        villager.getPersistentData().putBoolean(CULTIVATOR_GOAL_ADDED, true);
    }
}
