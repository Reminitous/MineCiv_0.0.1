package net.reminitous.mineciv.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import net.minecraftforge.event.entity.living.LivingHurtEvent;
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

    private static final String NBT_LAST_PVP_DENY_TICK = "MineCiv_LastPvpDenyTick";

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;

        // Only care about player vs player
        if (!(victim instanceof ServerPlayer victimPlayer)) return;

        Entity src = e.getSource().getEntity(); // attacker entity, may be null
        if (!(src instanceof ServerPlayer attackerPlayer)) return;

        // Prevent self damage
        if (attackerPlayer.getUUID().equals(victimPlayer.getUUID())) return;

        UUID attackerCiv = getPlayerCivId(level, attackerPlayer);
        UUID victimCiv = getPlayerCivId(level, victimPlayer);

        // --- Rule 0: Same civ never damages (anywhere) ---
        if (attackerCiv != null && attackerCiv.equals(victimCiv)) {
            e.setCanceled(true);
            denyMessage(attackerPlayer, "You cannot hurt members of your civilization.");
            return;
        }

        // --- Rule 1: Allies never damage (anywhere) ---
        if (attackerCiv != null && victimCiv != null && CivilizationManager.areAllies(level, attackerCiv, victimCiv)) {
            e.setCanceled(true);
            denyMessage(attackerPlayer, "You cannot hurt allies.");
            return;
        }

        // Territory based on where the victim is standing
        UUID ownerCiv = TerritoryManager.getOwnerCivId(level, victimPlayer.blockPosition());

        // --- Rule 2: Wilderness: allow PvP (except above rules) ---
        if (ownerCiv == null) {
            return;
        }

        // --- Rule 3: Claimed land rules ---
        boolean attackerIsDefender = attackerCiv != null && attackerCiv.equals(ownerCiv);
        boolean victimIsDefender = victimCiv != null && victimCiv.equals(ownerCiv);

        // Outsider -> Defender (blocked)
        if (!attackerIsDefender && victimIsDefender) {
            e.setCanceled(true);
            denyMessage(attackerPlayer, "You cannot attack defenders inside their territory.");
            return;
        }

        // Outsider -> Outsider (blocked) inside someone else's territory
        if (!attackerIsDefender && !victimIsDefender) {
            e.setCanceled(true);
            denyMessage(attackerPlayer, "You cannot fight inside another civilization's territory.");
            return;
        }

        // Defender -> Outsider (allowed)
        // Defender -> Defender won't happen because same-civ damage already blocked above.
    }

    /* ---------------- Helpers ---------------- */

    private static UUID getPlayerCivId(ServerLevel level, ServerPlayer player) {
        Optional<Civilization> civOpt = CivilizationManager.findPlayerCiv(level, player.getUUID());
        return civOpt.map(Civilization::id).orElse(null);
    }

    private static void denyMessage(ServerPlayer player, String msg) {
        long nowTick = player.server.getTickCount();
        long last = player.getPersistentData().getLong(NBT_LAST_PVP_DENY_TICK);

        // prevent spam: at most once per second
        if (nowTick - last < 20) return;

        player.getPersistentData().putLong(NBT_LAST_PVP_DENY_TICK, nowTick);
        player.sendSystemMessage(Component.literal(msg));
    }
}
