package net.reminitous.mineciv.events;

import net.reminitous.mineciv.territory.TerritoryRules;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public final class CombatEvents {

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent e) {
        if (e.getEntity().level().isClientSide()) return;

        if (!(e.getEntity() instanceof Player attacker)) return;
        if (!(e.getTarget() instanceof Player victim)) return;
        if (!(attacker.level() instanceof ServerLevel level)) return;

        BlockPos fightPos = victim.blockPosition(); // consistent choice: victim position
        TerritoryRules.AttackVerdict verdict = TerritoryRules.canAttack(level, attacker, victim, fightPos);

        if (verdict != TerritoryRules.AttackVerdict.ALLOW) {
            e.setCanceled(true);
            // Optional messages (avoid spam; you can rate-limit)
            // attacker.displayClientMessage(Component.literal("You can't attack that player here."), true);
        }
    }
}
