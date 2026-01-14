package net.reminitous.mineciv.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.war.WarEndManager;
import net.reminitous.mineciv.war.WarHealthManager;
import net.reminitous.mineciv.war.WarSavedData;
import net.reminitous.mineciv.war.WarState;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class WarDeathDamageEvents {

    private WarDeathDamageEvents() {}

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer victim)) return;
        if (!(victim.level() instanceof ServerLevel level)) return;

        // If victim isn't in a civ, ignore
        CivSavedData civData = CivSavedData.get(level.getServer());
        UUID victimCivId = civData.getPlayersCiv(victim.getUUID());
        if (victimCivId == null) return;

        // Must be in an ACTIVE war
        WarSavedData warData = WarSavedData.get(level.getServer());
        UUID warId = warData.getActiveWarId(victimCivId);
        if (warId == null) return;

        WarState war = warData.getWar(warId);
        if (war == null) return;
        if (war.phase() != WarState.Phase.ACTIVE) return;

        // Only apply if this death belongs to one of the two civs in this war
        if (!victimCivId.equals(war.attackerCivId()) && !victimCivId.equals(war.defenderCivId())) return;

        // Damage civ health for deaths
        WarHealthManager.damageCiv(level, warId, victimCivId, WarHealthManager.deathDamage());

        // Immediately check if war ended
        WarEndManager.tryEndIfDefeated(level, war);
    }
}
