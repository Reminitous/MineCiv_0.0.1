package net.reminitous.mineciv.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.item.ModItems;
import net.reminitous.mineciv.screen.MonumentMenu;

import javax.annotation.Nullable;
import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MineCiv.MOD_ID);

    public static final RegistryObject<Block> ALEXANDRITE_BLOCK = registerBlock("alexandrite_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(4f).requiresCorrectToolForDrops().sound(SoundType.AMETHYST)));

    public static final RegistryObject<Block> RAW_ALEXANDRITE_BLOCK = registerBlock("raw_alexandrite_block",
            () -> new Block(BlockBehaviour.Properties.of()
                    .strength(3f).requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> ALEXANDRITE_ORE = registerBlock("alexandrite_ore",
            () -> new DropExperienceBlock(UniformInt.of(2,4), BlockBehaviour.Properties.of()
                    .strength(4f).requiresCorrectToolForDrops()));

    public static final RegistryObject<Block> ALEXANDRITE_DEEPSLATE_ORE = registerBlock("alexandrite_deepslate_ore",
            () -> new DropExperienceBlock(UniformInt.of(3, 6),BlockBehaviour.Properties.of()
                    .strength(5f).requiresCorrectToolForDrops().sound(SoundType.DEEPSLATE)));

    public static final RegistryObject<Block> MONUMENT_BLOCK = registerBlock("monument",
            () -> new MonumentBlock());

    public static class MonumentBlock extends Block {
        public MonumentBlock() {
            super(Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(50.0f, 1200.0F)
                    .requiresCorrectToolForDrops());
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;

                ChunkClaimManager.ClaimData claim = ChunkClaimManager.getClaim(level, chunkX, chunkZ);

                if (claim != null && claim.ownerUUID.equals(player.getUUID())) {
                    serverPlayer.openMenu(new MonumentMenu.MonumentMenuProvider(pos), pos);
                    return InteractionResult.SUCCESS;
                } else if (claim != null) {
                    player.sendSystemMessage(Component.literal("You don't own this monument!"));
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }

        @Override
        public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
            super.setPlacedBy(level, pos, state, placer, stack);

            if (!level.isClientSide && placer != null) {
                // Get the chunk coordinates
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;

                // Claim the chunk for the player
                boolean claimed = ChunkClaimManager.claimChunk(level, chunkX, chunkZ, placer.getUUID(), pos);

                if (claimed) {
                    placer.sendSystemMessage(Component.literal("Chunk claimed at [" + chunkX + ", " + chunkZ + "]"));
                } else {
                    placer.sendSystemMessage(Component.literal("Chunk already claimed! Monument removed."));
                    // Remove the block if claim failed
                    level.destroyBlock(pos, true);
                }
            }
        }

        @Override
        public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
            if (!level.isClientSide && state.getBlock() != newState.getBlock()) {
                int chunkX = pos.getX() >> 4;
                int chunkZ = pos.getZ() >> 4;

                // Unclaim the chunk when monument is destroyed
                ChunkClaimManager.unclaimChunk(level, chunkX, chunkZ, pos);
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }

    private static <T extends Block> RegistryObject<T> registerBlock(String name, Supplier<T> block) {
        RegistryObject<T> toReturn = BLOCKS.register(name, block);
        registerBlockItems(name, toReturn);
        return toReturn;
    }

    private static <T extends Block> void registerBlockItems(String name, RegistryObject<T> block) {
        ModItems.ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}