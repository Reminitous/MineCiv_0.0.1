package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;

import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.territory.TerritoryManager;
import net.reminitous.mineciv.war.WarSavedData;
import net.reminitous.mineciv.war.WarState;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class TerritoryCombatEvents {

    private TerritoryCombatEvents() {}

    private static final String NBT_LAST_PVP_DENY_TICK = "MineCiv_LastPvPDenyTick";

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level)) return;

        // Only enforce PvP
        if (!(e.getEntity() instanceof ServerPlayer victim)) return;

        ServerPlayer attacker = resolveAttackingPlayer(e.getSource().getEntity(), e.getSource().getDirectEntity());
        if (attacker == null) return;

        // Same player edge case
        if (attacker.getUUID().equals(victim.getUUID())) return;

        UUID attackerCiv = CivSavedData.get(level.getServer()).getPlayersCiv(attacker.getUUID());
        UUID victimCiv = CivSavedData.get(level.getServer()).getPlayersCiv(victim.getUUID());

        // 1) Friendly fire OFF everywhere: same civ + allies cannot hurt each other anywhere
        if (attackerCiv != null && victimCiv != null) {
            if (attackerCiv.equals(victimCiv)) {
                e.setCanceled(true);
                return;
            }
            if (CivilizationManager.areAllies(level, attackerCiv, victimCiv)) {
                e.setCanceled(true);
                return;
            }
        }

        // 2) ACTIVE war exception: during ACTIVE war, attacker <-> victim can fight (anywhere)
        if (attackerCiv != null && victimCiv != null && isActiveWarBetween(level, attackerCiv, victimCiv)) {
            return; // allow
        }

        // 3) Territory ownership based on BOTH positions
        BlockPos aPos = attacker.blockPosition();
        BlockPos vPos = victim.blockPosition();

        UUID ownerAtAttacker = TerritoryManager.getOwnerCivId(level, aPos);
        UUID ownerAtVictim = TerritoryManager.getOwnerCivId(level, vPos);

        boolean attackerInWilderness = ownerAtAttacker == null;
        boolean victimInWilderness = ownerAtVictim == null;

        // Wilderness should always allow combat
        if (attackerInWilderness && victimInWilderness) {
            return;
        }

        // If victim is inside claimed territory (defender land), protect defenders:
        if (ownerAtVictim != null) {
            // If victim is a member of the owning civ, outsiders cannot damage them (unless war handled above)
            if (victimCiv != null && victimCiv.equals(ownerAtVictim)) {
                // Only the territory owner can damage defenders inside their own land
                if (attackerCiv != null && attackerCiv.equals(ownerAtVictim)) {
                    return; // defender attacking inside their land (could be civil war case already blocked above)
                }

                e.setCanceled(true);
                deny(attacker, "You cannot attack defenders in their territory.");
                return;
            }

            // Outsiders fighting outsiders inside someone else's land is blocked
            if (attackerCiv == null || !attackerCiv.equals(ownerAtVictim)) {
                e.setCanceled(true);
                deny(attacker, "You cannot fight inside another civilization's territory.");
                return;
            }

            // Attacker is the territory owner civ and victim is outsider -> allowed
            return;
        }

        // At this point: victim is in wilderness, attacker might be in claimed land or wilderness.
        // Wilderness allows combat, BUT we still prevent outsiders fighting inside a claimed land when the attacker is inside it?
        // Your “defenders can fight outsiders” implies: if attacker is inside their own land, they can attack out.
        if (ownerAtAttacker != null) {
            // If attacker is inside SOMEONE'S territory:
            // Only allow if attacker is the owner of that territory (defender shooting out).
            if (attackerCiv != null && attackerCiv.equals(ownerAtAttacker)) {
                return; // defender attacking outward into wilderness
            }

            // Otherwise attacker is an outsider standing inside someone else's claimed land trying to fight into wilderness
            e.setCanceled(true);
            deny(attacker, "You cannot initiate combat from within another civilization's territory.");
            return;
        }

        // victim wilderness, attacker wilderness handled earlier, so remaining case is victim wilderness & attacker wilderness already returned.
        // Default allow.
    }

    /* ---------------- Helpers ---------------- */

    private static ServerPlayer resolveAttackingPlayer(Entity sourceEntity, Entity directEntity) {
        if (sourceEntity instanceof ServerPlayer sp) return sp;

        if (directEntity instanceof Projectile proj) {
            Entity owner = proj.getOwner();
            if (owner instanceof ServerPlayer sp) return sp;
        }

        if (directEntity instanceof ServerPlayer sp) return sp;

        return null;
    }

    private static boolean isActiveWarBetween(ServerLevel level, UUID civA, UUID civB) {
        if (civA == null || civB == null) return false;

        WarSavedData warData = WarSavedData.get(level.getServer());
        UUID warIdA = warData.getActiveWarId(civA);
        if (warIdA == null) return false;

        WarState w = warData.getWar(warIdA);
        if (w == null) return false;

        if (w.phase() != WarState.Phase.ACTIVE) return false;

        boolean match1 = civA.equals(w.attackerCivId()) && civB.equals(w.defenderCivId());
        boolean match2 = civA.equals(w.defenderCivId()) && civB.equals(w.attackerCivId());
        return match1 || match2;
    }

    private static void deny(ServerPlayer player, String msg) {
        long nowTick = player.server.getTickCount();
        long last = player.getPersistentData().getLong(NBT_LAST_PVP_DENY_TICK);

        // Spam guard: at most once per second
        if (nowTick - last < 20) return;

        player.getPersistentData().putLong(NBT_LAST_PVP_DENY_TICK, nowTick);
        player.sendSystemMessage(Component.literal(msg));
    }
}
