package net.reminitous.mineciv.npc;

import net.reminitous.mineciv.civ.CivClass;

import java.util.EnumSet;
import java.util.Set;

public enum NpcRoleType {
    // Agricultural
    FARMER,
    SHEPHERD,
    LUMBERJACK,

    // Warlike
    PATROL,
    KNIGHT,
    ARCHER,

    // Technology
    WORKER,
    MINER,

    // Mystic
    WITCH,
    WIZARD,
    ENCHANTER;

    public static Set<NpcRoleType> allowedFor(CivClass classType) {
        if (classType == null) classType = CivClass.AGRICULTURAL;

        return switch (classType) {
            case AGRICULTURAL -> EnumSet.of(FARMER, SHEPHERD, LUMBERJACK);
            case WARLIKE -> EnumSet.of(PATROL, KNIGHT, ARCHER);
            case TECHNOLOGY -> EnumSet.of(WORKER, MINER);
            case MYSTIC -> EnumSet.of(WITCH, WIZARD, ENCHANTER);
        };
    }
}
