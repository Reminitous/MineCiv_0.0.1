package net.reminitous.mineciv.monument;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.reminitous.mineciv.registry.ModBlockEntities;

import java.util.UUID;

public final class MonumentBlockEntity extends BlockEntity {

    private UUID civId;

    public MonumentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONUMENT.get(), pos, state);
    }

    public UUID getCivId() {
        return civId;
    }

    public boolean isBound() {
        return civId != null;
    }

    /** Preferred binding method used by packets + server logic. */
    public void bindToCiv(UUID civId) {
        this.civId = civId;
        setChanged();
    }

    /** Backwards-compatible alias (if older code still calls setCivId). */
    public void setCivId(UUID civId) {
        bindToCiv(civId);
    }

    /* ---------------- NBT (1.21.1 signatures) ---------------- */

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (civId != null) tag.putUUID("CivId", civId);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        civId = tag.hasUUID("CivId") ? tag.getUUID("CivId") : null;
    }
}
