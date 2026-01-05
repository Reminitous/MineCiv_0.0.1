package net.reminitous.mineciv.npc;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;

import net.reminitous.mineciv.civ.CivClassType;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CivNpcManager {

    private CivNpcManager() {}

    // Tuning knobs
    public static int targetNpcCount(Civilization civ) {
        // Simple curve: level 1-2 => 1 NPC, 3-4 => 2 NPC, etc., capped at 12
        int lvl = Math.max(1, civ.civLevel());
        int target = 1 + (lvl - 1) / 2;
        return Math.min(12, target);
    }

    public static void tick(ServerLevel level) {
        CivSavedData data = CivSavedData.get(level.getServer());

        for (Civilization civ : data.civs().values()) {
            // Must have a monument location to spawn around
            BlockPos monumentPos = civ.monumentPos();
            if (monumentPos == null) continue;

            String dim = civ.monumentDimId();
            if (dim == null || !dim.equals(level.dimension().location().toString())) continue;

            // Clean dead NPC ids
            List<UUID> toRemove = new ArrayList<>();
            int aliveCount = 0;
            for (UUID id : civ.npcIds()) {
                var ent = level.getEntity(id);
                if (ent instanceof Villager && ent.isAlive()) aliveCount++;
                else toRemove.add(id);
            }
            if (!toRemove.isEmpty()) {
                for (UUID id : toRemove) civ.removeNpcId(id);
                data.putCiv(civ);
            }

            int target = targetNpcCount(civ);
            if (aliveCount >= target) continue;

            // Spawn one NPC per tick pass until target is reached (slow ramp up)
            spawnOneNpc(level, civ, monumentPos);
            data.putCiv(civ);
        }
    }

    private static void spawnOneNpc(ServerLevel level, Civilization civ, BlockPos monumentPos) {
        VillagerProfession prof = chooseProfession(level.random.nextInt(100), civ.classType());
        String roleName = roleNameForProfession(prof, civ.classType());

        // Try a few nearby positions
        for (int i = 0; i < 12; i++) {
            int dx = level.random.nextInt(9) - 4;
            int dz = level.random.nextInt(9) - 4;
            BlockPos pos = monumentPos.offset(dx, 1, dz);

            // Find ground
            BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos);
            if (!level.getBlockState(ground.below()).isSolid()) continue;

            Villager v = EntityType.VILLAGER.create(level);
            if (v == null) return;

            v.moveTo(ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
            v.finalizeSpawn(level, level.getCurrentDifficultyAt(ground), MobSpawnType.EVENT, null);

            // Set villager type based on biome (optional). Vanilla will handle ok; we keep PLAINS.
            v.setVillagerData(v.getVillagerData().setType(VillagerType.PLAINS).setProfession(prof));

            // Custom name
            v.setCustomName(Component.literal("MineCiv " + roleName));
            v.setCustomNameVisible(false);

            // Mark as persistent so it doesn't despawn
            v.setPersistenceRequired();

            // Tag civ ownership in persistent data
            v.getPersistentData().putUUID("MineCivCivId", civ.id());
            v.getPersistentData().putString("MineCivRole", roleName);

            level.addFreshEntity(v);

            civ.addNpcId(v.getUUID());
            return;
        }
    }

    private static VillagerProfession chooseProfession(int roll, CivClassType type) {
        // Simple role mixes per class (tune later)
        return switch (type) {
            case AGRICULTURAL -> {
                if (roll < 60) yield VillagerProfession.FARMER;
                if (roll < 85) yield VillagerProfession.SHEPHERD;
                else yield VillagerProfession.FLETCHER; // "lumberjack" placeholder (wood-related)
            }
            case WARLIKE -> {
                if (roll < 50) yield VillagerProfession.WEAPONSMITH; // "blacksmith/knight"
                if (roll < 75) yield VillagerProfession.ARMORER;
                else yield VillagerProfession.FLETCHER; // "archer"
            }
            case TECHNOLOGY -> {
                if (roll < 45) yield VillagerProfession.TOOLSMITH; // "engineer"
                if (roll < 75) yield VillagerProfession.MASON;     // "factory worker"
                else yield VillagerProfession.TOOLSMITH;               // NOTE: may not exist in vanilla; see below
            }
            case MYSTIC -> {
                if (roll < 70) yield VillagerProfession.CLERIC;    // "witch/enchanter"
                else yield VillagerProfession.LIBRARIAN;
            }
            case MERCHANT -> {
                if (roll < 80) yield VillagerProfession.CARTOGRAPHER;
                else yield VillagerProfession.LEATHERWORKER;
            }
        };
    }

    private static String roleNameForProfession(VillagerProfession prof, CivClassType type) {
        // Friendly labels for the nameplate
        if (type == CivClassType.WARLIKE && prof == VillagerProfession.FLETCHER) return "Archer";
        if (type == CivClassType.WARLIKE && (prof == VillagerProfession.WEAPONSMITH || prof == VillagerProfession.ARMORER)) return "Guard";
        if (type == CivClassType.AGRICULTURAL && prof == VillagerProfession.FARMER) return "Farmer";
        if (type == CivClassType.AGRICULTURAL && prof == VillagerProfession.SHEPHERD) return "Shepherd";
        if (type == CivClassType.AGRICULTURAL && prof == VillagerProfession.FLETCHER) return "Lumberjack";
        if (type == CivClassType.TECHNOLOGY && prof == VillagerProfession.TOOLSMITH) return "Engineer";
        if (type == CivClassType.TECHNOLOGY && prof == VillagerProfession.MASON) return "Factory Worker";
        if (type == CivClassType.MYSTIC && prof == VillagerProfession.CLERIC) return "Witch";
        if (type == CivClassType.MYSTIC && prof == VillagerProfession.LIBRARIAN) return "Enchanter";
        if (type == CivClassType.MERCHANT) return "Merchant";
        return "Villager";
    }
}
