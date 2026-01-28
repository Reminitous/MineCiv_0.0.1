package net.reminitous.mineciv.util;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.npc.MineCivNpcBase;

import java.util.UUID;

public final class CivLookupUtil {

    private CivLookupUtil() {}

    /** If entity is a civ NPC, return its civId. If player, find civ where player is member. */
    public static UUID civIdOf(Entity e, MinecraftServer server) {
        if (e == null || server == null) return null;

        if (e instanceof MineCivNpcBase npc) {
            return npc.getCivId();
        }

        if (e instanceof Player p) {
            Civilization civ = civOfPlayer(p.getUUID(), server);
            return civ == null ? null : civ.id();
        }

        return null;
    }

    public static Civilization civOfPlayer(UUID playerId, MinecraftServer server) {
        if (playerId == null || server == null) return null;

        CivSavedData data = CivSavedData.get(server);
        for (Civilization civ : data.civs().values()) {
            if (civ != null && civ.isMember(playerId)) return civ;
        }
        return null;
    }

    public static boolean isSameCiv(Entity a, Entity b, MinecraftServer server) {
        UUID ca = civIdOf(a, server);
        UUID cb = civIdOf(b, server);
        return ca != null && ca.equals(cb);
    }
}
