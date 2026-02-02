package net.reminitous.mineciv.monument;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.reminitous.mineciv.registry.ModBlockEntities;

import java.util.UUID;

public final class MonumentBlockEntity extends BlockEntity {

    private UUID civId;

    /* ---------------- Population maintenance ---------------- */

    // Prevents constant checks / spawn spam
    private int maintainCooldownTicks = 0;

    // 20 tps * 15s = every 15 seconds
    private static final int MAINTAIN_INTERVAL_TICKS = 20 * 15;

    public MonumentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONUMENT.get(), pos, state);
    }

    /* ---------------- Civ binding ---------------- */

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

    /** Convenience helper (used by packets / creation flow) */
    public void bindToCiv(UUID civId) {
        setCivId(civId);
    }

    /* ---------------- Server tick ---------------- */

    public static void serverTick(
            Level level,
            BlockPos pos,
            BlockState state,
            MonumentBlockEntity be
    ) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (be.civId == null) return;

        // Cooldown throttle
        if (be.maintainCooldownTicks > 0) {
            be.maintainCooldownTicks--;
            return;
        }

        be.maintainCooldownTicks = MAINTAIN_INTERVAL_TICKS;

        var server = serverLevel.getServer();
        var data = net.reminitous.mineciv.civ.CivSavedData.get(server);
        var civ = data.getCiv(be.civId);

        if (civ == null) return;

        // 🔑 Single authority for NPC population & roles
        net.reminitous.mineciv.npc.CivNpcSpawnManager.maintainOneCiv(
                server,
                data,
                civ
        );
    }

    /* ---------------- NBT (1.21.1) ---------------- */

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);

        if (civId != null) {
            tag.putUUID("CivId", civId);
        }

        tag.putInt("MaintainCooldown", maintainCooldownTicks);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        civId = tag.hasUUID("CivId") ? tag.getUUID("CivId") : null;
        maintainCooldownTicks = tag.contains("MaintainCooldown")
                ? tag.getInt("MaintainCooldown")
                : 0;
    }
}
