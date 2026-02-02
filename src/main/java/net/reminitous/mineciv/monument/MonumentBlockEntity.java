package net.reminitous.mineciv.monument;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import net.reminitous.mineciv.registry.ModBlockEntities;

import java.util.UUID;

public final class MonumentBlockEntity extends BlockEntity {

    private UUID civId;

    // --- population control ---
    private int spawnCooldownTicks = 0;

    // how often we check population (20 tps * 15s = 300)
    private static final int CHECK_INTERVAL_TICKS = 20 * 15;

    // how far from monument we count NPCs (fallback if you don't have bounds yet)
    private static final int COUNT_RADIUS = 64;

    public MonumentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONUMENT.get(), pos, state);
    }

    public UUID getCivId() {
        return civId;
    }

    public boolean isBound() {
        return civId != null;
    }

    public void setCivId(UUID civId) {
        this.civId = civId;
        setChanged();
    }

    /** Convenience name (used by packets) */
    public void bindToCiv(UUID civId) {
        setCivId(civId);
    }

    /* ---------------- Server tick ---------------- */

    public static void serverTick(Level level, BlockPos pos, BlockState state, MonumentBlockEntity be) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (be.civId == null) return;

        if (be.spawnCooldownTicks > 0) {
            be.spawnCooldownTicks--;
            return;
        }

        // Reset cooldown first, so even if something goes wrong we don't spam.
        be.spawnCooldownTicks = CHECK_INTERVAL_TICKS;

        // 1) Determine cap by civ level
        int civLevel = getCivLevel(serverLevel, be.civId); // implement in your CivSavedData
        int cap = getNpcCapForLevel(civLevel);

        // 2) Count existing civ NPCs near monument (or inside bounds if you have them)
        int existing = countCivNpcsNearby(serverLevel, be.civId, pos, COUNT_RADIUS);

        // 3) Spawn only up to the cap (1 at a time to avoid bursts)
        if (existing < cap) {
            spawnOneNpc(serverLevel, be.civId, pos);
        }
    }

    private static int getCivLevel(ServerLevel level, UUID civId) {
        // Hook this into your existing civ data.
        // Example (adjust to your real API):
        var data = net.reminitous.mineciv.civ.CivSavedData.get(level.getServer());
        var civ = data.getCiv(civId);
        return civ != null ? civ.getLevel() : 1;
    }

    private static int getNpcCapForLevel(int level) {
        // Tune these however you like:
        // L1=2, L2=4, L3=7, L4=11, L5=16...
        return switch (Math.max(1, level)) {
            case 1 -> 2;
            case 2 -> 4;
            case 3 -> 7;
            case 4 -> 11;
            default -> 11 + (level - 4) * 5;
        };
    }

    private static int countCivNpcsNearby(ServerLevel level, UUID civId, BlockPos monumentPos, int radius) {
        AABB box = new AABB(
                monumentPos.getX() - radius, monumentPos.getY() - 32, monumentPos.getZ() - radius,
                monumentPos.getX() + radius, monumentPos.getY() + 32, monumentPos.getZ() + radius
        );

        // Count only your civ NPC base class
        return level.getEntitiesOfClass(
                net.reminitous.mineciv.npc.MineCivNpcBase.class,
                box,
                npc -> civId.equals(npc.getCivId())
        ).size();
    }

    private static void spawnOneNpc(ServerLevel level, UUID civId, BlockPos monumentPos) {
        // IMPORTANT:
        // Replace this with your actual NPC type(s). If you have multiple roles,
        // pick one based on civ level, weights, etc.

        // Example placeholder:
        Entity e = net.reminitous.mineciv.registry.ModEntities.CIV_WORKER.get().create(level);
        if (!(e instanceof net.reminitous.mineciv.npc.MineCivNpcBase npc)) return;

        BlockPos spawnPos = findSpawnPosNearMonument(level, monumentPos, level.random);

        npc.moveTo(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5,
                level.random.nextFloat() * 360.0F, 0.0F);

        npc.bindToCiv(civId, monumentPos);
        // optionally set role:
        // npc.setRole("worker");

        level.addFreshEntity(npc);
    }

    private static BlockPos findSpawnPosNearMonument(ServerLevel level, BlockPos monumentPos, RandomSource rand) {
        // Find a safe-ish spot near the monument on the surface
        for (int i = 0; i < 10; i++) {
            int dx = rand.nextIntBetweenInclusive(-4, 4);
            int dz = rand.nextIntBetweenInclusive(-4, 4);

            BlockPos xz = monumentPos.offset(dx, 0, dz);
            BlockPos top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, xz);

            // Ensure headroom
            if (level.getBlockState(top).isAir() && level.getBlockState(top.above()).isAir()) {
                return top;
            }
        }
        // fallback: just above monument
        return monumentPos.above();
    }

    /* ---------------- NBT (1.21.1 signatures) ---------------- */

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (civId != null) tag.putUUID("CivId", civId);
        tag.putInt("SpawnCooldown", spawnCooldownTicks);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        civId = tag.hasUUID("CivId") ? tag.getUUID("CivId") : null;
        spawnCooldownTicks = tag.contains("SpawnCooldown") ? tag.getInt("SpawnCooldown") : 0;
    }
}
