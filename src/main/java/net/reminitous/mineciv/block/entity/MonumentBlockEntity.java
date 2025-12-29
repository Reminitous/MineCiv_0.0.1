package net.reminitous.mineciv.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.reminitous.mineciv.civ.CivilizationType;

import javax.annotation.Nullable;

public class MonumentBlockEntity extends BlockEntity {

    private static final String NBT_LINKED_CHEST = "LinkedChest";
    private static final String NBT_CIV_TYPE = "CivType";

    // ---- Stored data ----
    private @Nullable BlockPos linkedChest;
    private CivilizationType civType = CivilizationType.FARMER; // default

    public MonumentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONUMENT_BE.get(), pos, state);
    }

    // --------------------
    // Civilization
    // --------------------
    public CivilizationType getCivType() {
        return civType;
    }

    public void setCivType(CivilizationType civType) {
        this.civType = civType;
        setChanged();

        // Sync block update so client UI refreshes
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // --------------------
    // Linked storage
    // --------------------
    public void setLinkedChest(@Nullable BlockPos pos) {
        this.linkedChest = pos;
        setChanged();
    }

    public @Nullable BlockPos getLinkedChest() {
        return linkedChest;
    }

    // --------------------
    // Save / Load
    // --------------------
    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);

        if (linkedChest != null) {
            tag.putLong(NBT_LINKED_CHEST, linkedChest.asLong());
        }

        tag.putString(NBT_CIV_TYPE, civType.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);

        linkedChest = tag.contains(NBT_LINKED_CHEST)
                ? BlockPos.of(tag.getLong(NBT_LINKED_CHEST))
                : null;

        if (tag.contains(NBT_CIV_TYPE)) {
            try {
                civType = CivilizationType.valueOf(tag.getString(NBT_CIV_TYPE));
            } catch (IllegalArgumentException e) {
                civType = CivilizationType.FARMER;
            }
        } else {
            civType = CivilizationType.FARMER;
        }
    }
}
