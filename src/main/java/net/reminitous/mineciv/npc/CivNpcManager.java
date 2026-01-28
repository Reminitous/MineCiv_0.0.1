package net.reminitous.mineciv.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.levelgen.Heightmap;

import net.reminitous.mineciv.civ.CivClass;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CivNpcManager {

    private CivNpcManager() {}

    public static int targetNpcCount(Civilization civ) {
        int lvl = Math.max(1, civ.civLevel());
        int target = 1 + (lvl - 1) / 2;
        return Math.min(12, target);
    }

    public static void tick(ServerLevel level) {
        CivSavedData data = CivSavedData.get(level.getServer());

        for (Civilization civ : data.civs().values()) {
            BlockPos monumentPos = civ.monumentPos();
            if (monumentPos == null) continue;

            String dim = civ.monumentDimId();
            if (dim == null || !dim.equals(level.dimension().location().toString())) continue;

            if (!level.hasChunkAt(monumentPos)) continue;

            // Clean dead NPC ids
            List<UUID> toRemove = new ArrayList<>();
            int aliveCount = 0;
            for (UUID id : civ.npcIds()) {
                Entity ent = level.getEntity(id);
                if (ent != null && ent.isAlive()) aliveCount++;
                else toRemove.add(id);
            }
            if (!toRemove.isEmpty()) {
                for (UUID id : toRemove) civ.removeNpcId(id);
                data.putCiv(civ);
            }

            int target = targetNpcCount(civ);
            if (aliveCount >= target) continue;

            if (spawnOneNpc(level, civ, monumentPos)) {
                data.putCiv(civ);
            }
        }
    }

    private static boolean spawnOneNpc(ServerLevel level, Civilization civ, BlockPos monumentPos) {
        CivClass ct = civ.classType();
        if (ct == null) ct = CivClass.AGRICULTURAL;

        VillagerProfession prof = chooseProfession(level.random.nextInt(100), ct);
        String roleName = roleNameForProfession(prof, ct);

        // WARLIKE Guards are real fighters: spawn an Iron Golem
        boolean spawnGolemGuard = (ct == CivClass.WARLIKE && "Guard".equals(roleName));

        for (int i = 0; i < 12; i++) {
            int dx = level.random.nextInt(9) - 4;
            int dz = level.random.nextInt(9) - 4;

            BlockPos probe = monumentPos.offset(dx, 0, dz);
            if (!level.hasChunkAt(probe)) continue;

            BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, probe);

            if (!level.getBlockState(ground.below()).isSolid()) continue;

            if (spawnGolemGuard) {
                IronGolem g = EntityType.IRON_GOLEM.create(level);
                if (g == null) return false;

                g.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
                g.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null);

                g.setCustomName(Component.literal("MineCiv Guard"));
                g.setCustomNameVisible(false);
                g.setPersistenceRequired();

                g.getPersistentData().putUUID("MineCivCivId", civ.id());
                g.getPersistentData().putString("MineCivRole", "Guard");

                level.addFreshEntity(g);
                civ.addNpcId(g.getUUID());
                return true;
            }

            Villager v = EntityType.VILLAGER.create(level);
            if (v == null) return false;

            v.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
            v.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null);

            v.setVillagerData(v.getVillagerData().setType(VillagerType.PLAINS).setProfession(prof));

            v.setCustomName(Component.literal("MineCiv " + roleName));
            v.setCustomNameVisible(false);
            v.setPersistenceRequired();

            v.getPersistentData().putUUID("MineCivCivId", civ.id());
            v.getPersistentData().putString("MineCivRole", roleName);

            level.addFreshEntity(v);
            civ.addNpcId(v.getUUID());
            return true;
        }

        return false;
    }

    private static VillagerProfession chooseProfession(int roll, CivClass type) {
        return switch (type) {
            case AGRICULTURAL -> {
                if (roll < 60) yield VillagerProfession.FARMER;
                if (roll < 85) yield VillagerProfession.SHEPHERD;
                else yield VillagerProfession.FLETCHER; // "lumberjack" placeholder
            }
            case WARLIKE -> {
                if (roll < 50) yield VillagerProfession.WEAPONSMITH; // Guard
                if (roll < 75) yield VillagerProfession.ARMORER;     // Guard
                else yield VillagerProfession.FLETCHER;              // Archer
            }
            case TECHNOLOGY -> {
                if (roll < 45) yield VillagerProfession.TOOLSMITH; // Engineer
                if (roll < 75) yield VillagerProfession.MASON;     // Factory Worker
                else yield VillagerProfession.TOOLSMITH;
            }
            case MYSTIC -> {
                if (roll < 70) yield VillagerProfession.CLERIC;    // Witch
                else yield VillagerProfession.LIBRARIAN;           // Enchanter
            }
            case MERCHANT -> {
                if (roll < 80) yield VillagerProfession.CARTOGRAPHER;
                else yield VillagerProfession.LEATHERWORKER;
            }
        };
    }

    private static String roleNameForProfession(VillagerProfession prof, CivClass type) {
        if (type == CivClass.WARLIKE && prof == VillagerProfession.FLETCHER) return "Archer";
        if (type == CivClass.WARLIKE && (prof == VillagerProfession.WEAPONSMITH || prof == VillagerProfession.ARMORER)) return "Guard";

        if (type == CivClass.AGRICULTURAL && prof == VillagerProfession.FARMER) return "Farmer";
        if (type == CivClass.AGRICULTURAL && prof == VillagerProfession.SHEPHERD) return "Shepherd";
        if (type == CivClass.AGRICULTURAL && prof == VillagerProfession.FLETCHER) return "Lumberjack";

        if (type == CivClass.TECHNOLOGY && prof == VillagerProfession.TOOLSMITH) return "Engineer";
        if (type == CivClass.TECHNOLOGY && prof == VillagerProfession.MASON) return "Factory Worker";

        if (type == CivClass.MYSTIC && prof == VillagerProfession.CLERIC) return "Witch";
        if (type == CivClass.MYSTIC && prof == VillagerProfession.LIBRARIAN) return "Enchanter";

        if (type == CivClass.MERCHANT) return "Merchant";
        return "Villager";
    }
}
