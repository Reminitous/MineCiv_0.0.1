package net.reminitous.mineciv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;

public class MonumentBlockEntity extends BlockEntity {

    private @Nullable BlockPos linkedChest;

    public MonumentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONUMENT_BE.get(), pos, state);
    }

    public void setLinkedChest(@Nullable BlockPos pos) {
        this.linkedChest = pos;
        setChanged();
    }

    public @Nullable BlockPos getLinkedChest() {
        return linkedChest;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        if (linkedChest != null) {
            tag.putLong("LinkedChest", linkedChest.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        linkedChest = tag.contains("LinkedChest") ? BlockPos.of(tag.getLong("LinkedChest")) : null;
    }
}
