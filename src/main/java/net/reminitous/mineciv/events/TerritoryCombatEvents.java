package net.reminitous.mineciv.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.territory.TerritoryManager;

import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class TerritoryCombatEvents {

    private TerritoryCombatEvents() {}

    private static final String NBT_LAST_DENY_TICK = "MineCiv_LastCombatDenyTick";

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level)) return;

        // Player-vs-player only
        if (!(e.getEntity() instanceof ServerPlayer victim)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer attacker)) return;

        UUID attackerCiv = getPlayerCivId(level, attacker);
        UUID victimCiv = getPlayerCivId(level, victim);

        // 1) Same civ never damages each other
        if (attackerCiv != null && attackerCiv.equals(victimCiv)) {
            e.setCanceled(true);
            denyMessage(attacker, "You cannot hurt members of your civilization.");
            return;
        }

        // 2) Allies never damage each other
        if (attackerCiv != null && victimCiv != null && CivilizationManager.areAllies(level, attackerCiv, victimCiv)) {
            e.setCanceled(true);
            denyMessage(attacker, "You cannot hurt allies.");
            return;
        }

        // 3) Territory asymmetric combat:
        // If attacker is standing inside some civ's territory, and victim is that civ's member,
        // then attacker cannot damage them unless attacker is also that civ.
        UUID ownerAtAttackerPos = TerritoryManager.getOwnerCivId(level, attacker.blockPosition());
        if (ownerAtAttackerPos != null) {
            if (victimCiv != null && victimCiv.equals(ownerAtAttackerPos)) {
                if (attackerCiv == null || !attackerCiv.equals(ownerAtAttackerPos)) {
                    e.setCanceled(true);
                    denyMessage(attacker, "You cannot attack territory members while inside their land.");
                }
            }
        }
    }

    /* ---------------- Helpers ---------------- */

    private static UUID getPlayerCivId(ServerLevel level, ServerPlayer player) {
        Optional<Civilization> civOpt = CivilizationManager.findPlayerCiv(level, player.getUUID());
        return civOpt.map(Civilization::id).orElse(null);
    }

    private static void denyMessage(ServerPlayer player, String msg) {
        long nowTick = player.server.getTickCount();
        long last = player.getPersistentData().getLong(NBT_LAST_DENY_TICK);

        // No spam: at most once per second
        if (nowTick - last < 20) return;

        player.getPersistentData().putLong(NBT_LAST_DENY_TICK, nowTick);
        player.sendSystemMessage(Component.literal(msg));
    }
}
