package net.reminitous.mineciv.territory;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Optional;
import java.util.UUID;

public final class TerritoryRules {

    private TerritoryRules() {}

    public static Optional<UUID> playerCivId(ServerLevel level, Player player) {
        return CivilizationManager.findPlayerCiv(level, player.getUUID()).map(Civilization::id);
    }

    public static UUID ownerCivIdAt(ServerLevel level, BlockPos pos) {
        return TerritoryManager.getOwnerCivId(level, pos);
    }

    public static boolean canOpenContainer(ServerLevel level, Player player, BlockPos pos, BlockEntity be) {
        UUID owner = ownerCivIdAt(level, pos);
        if (owner == null) return true; // unclaimed: free access (or change if you want)

        Optional<UUID> pc = playerCivId(level, player);
        if (pc.isEmpty()) return false;

        UUID playerCiv = pc.get();
        if (owner.equals(playerCiv)) return true;

        // allow allies? your design says allies can’t hurt each other; chest access you can choose:
        return CivilizationManager.areAllies(level, owner, playerCiv);
    }

    public static boolean canBuild(ServerLevel level, Player player, BlockPos pos) {
        UUID owner = ownerCivIdAt(level, pos);
        if (owner == null) return true;

        Optional<UUID> pc = playerCivId(level, player);
        if (pc.isEmpty()) return false;

        UUID playerCiv = pc.get();
        return owner.equals(playerCiv); // strict: only members build in their civ land
    }

    public static AttackVerdict canAttack(ServerLevel level, Player attacker, Player victim, BlockPos fightPos) {
        Optional<UUID> aCiv = playerCivId(level, attacker);
        Optional<UUID> vCiv = playerCivId(level, victim);

        // If either isn’t in a civ:
        // - Your rules: unclaimed territory allows freedom for combat; but in claimed territories, enforce restrictions.
        UUID territoryOwner = ownerCivIdAt(level, fightPos);

        // Allies / same civ never damage each other anywhere
        if (aCiv.isPresent() && vCiv.isPresent()) {
            if (aCiv.get().equals(vCiv.get())) return AttackVerdict.DENY_FRIENDLY;
            if (CivilizationManager.areAllies(level, aCiv.get(), vCiv.get())) return AttackVerdict.DENY_ALLY;
        }

        if (territoryOwner == null) {
            return AttackVerdict.ALLOW; // unclaimed: normal PvP
        }

        // If victim is in the owning civ and attacker is not, deny attacker damaging defenders
        if (vCiv.isPresent() && territoryOwner.equals(vCiv.get())) {
            if (aCiv.isEmpty() || !territoryOwner.equals(aCiv.get())) {
                return AttackVerdict.DENY_INTRUDER_CANNOT_HIT_DEFENDER;
            }
        }

        // Defender hitting intruder inside defender territory is allowed
        if (aCiv.isPresent() && territoryOwner.equals(aCiv.get())) {
            if (vCiv.isEmpty() || !territoryOwner.equals(vCiv.get())) {
                return AttackVerdict.ALLOW;
            }
        }

        // Default allow (e.g., two outsiders fighting inside a third party’s land—define your preference)
        // You might want DENY here to prevent fighting in others’ territory; your spec didn’t forbid it.
        return AttackVerdict.ALLOW;
    }

    public enum AttackVerdict {
        ALLOW,
        DENY_FRIENDLY,
        DENY_ALLY,
        DENY_INTRUDER_CANNOT_HIT_DEFENDER
    }
}
