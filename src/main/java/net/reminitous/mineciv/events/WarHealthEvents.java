package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;

import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.territory.TerritoryManager;
import net.reminitous.mineciv.war.*;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class WarHealthEvents {

    private WarHealthEvents() {}

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer victim)) return;

        ServerPlayer killer = resolveAttackingPlayer(e.getSource().getEntity(), e.getSource().getDirectEntity());
        if (killer == null) return;

        UUID victimCiv = CivSavedData.get(level.getServer()).getPlayersCiv(victim.getUUID());
        UUID killerCiv = CivSavedData.get(level.getServer()).getPlayersCiv(killer.getUUID());
        if (victimCiv == null || killerCiv == null) return;

        WarState war = getActiveWarBetween(level, killerCiv, victimCiv);
        if (war == null) return;

        // Damage the victim side
        WarHealthManager.damageCiv(level, war.warId(), victimCiv, WarHealthManager.deathDamage());

        // End check
        WarEndManager.tryEndIfDefeated(level, war);
    }

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getPlayer() instanceof ServerPlayer breaker)) return;

        BlockPos pos = e.getPos();
        UUID territoryOwner = TerritoryManager.getOwnerCivId(level, pos);
        if (territoryOwner == null) return; // wilderness doesn't affect war health

        UUID breakerCiv = CivSavedData.get(level.getServer()).getPlayersCiv(breaker.getUUID());
        if (breakerCiv == null) return;

        // Only count breaking enemy blocks inside enemy territory during ACTIVE war
        if (breakerCiv.equals(territoryOwner)) return;

        WarState war = getActiveWarBetween(level, breakerCiv, territoryOwner);
        if (war == null) return;

        // Damage the territory owner side
        WarHealthManager.damageCiv(level, war.warId(), territoryOwner, WarHealthManager.blockBreakDamage());

        // End check
        WarEndManager.tryEndIfDefeated(level, war);
    }

    /* ---------------- Helpers ---------------- */

    private static WarState getActiveWarBetween(ServerLevel level, UUID civA, UUID civB) {
        WarSavedData warData = WarSavedData.get(level.getServer());
        UUID warIdA = warData.getActiveWarId(civA);
        if (warIdA == null) return null;

        WarState w = warData.getWar(warIdA);
        if (w == null) return null;
        if (w.phase() != WarState.Phase.ACTIVE) return null;

        boolean match1 = civA.equals(w.attackerCivId()) && civB.equals(w.defenderCivId());
        boolean match2 = civA.equals(w.defenderCivId()) && civB.equals(w.attackerCivId());
        return (match1 || match2) ? w : null;
    }

    private static ServerPlayer resolveAttackingPlayer(Entity sourceEntity, Entity directEntity) {
        if (sourceEntity instanceof ServerPlayer sp) return sp;

        if (directEntity instanceof Projectile proj) {
            Entity owner = proj.getOwner();
            if (owner instanceof ServerPlayer sp) return sp;
        }

        if (directEntity instanceof ServerPlayer sp) return sp;

        return null;
    }
}
