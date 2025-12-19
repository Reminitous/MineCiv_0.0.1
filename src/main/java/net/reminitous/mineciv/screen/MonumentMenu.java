package net.reminitous.mineciv.screen;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.network.IContainerFactory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.block.ChunkClaimManager;

import javax.annotation.Nullable;
import java.util.*;

public class MonumentMenu extends AbstractContainerMenu {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MineCiv.MOD_ID);

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        // Return empty stack for now
        return net.minecraft.world.item.ItemStack.EMPTY;
    }


    public static final RegistryObject<MenuType<MonumentMenu>> MONUMENT_MENU = MENUS.register("monument_menu",
            () -> IForgeMenuType.create((windowId, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                return new MonumentMenu(windowId, inv, pos);
            }));

    private final BlockPos monumentPos;
    private final int chunkX;
    private final int chunkZ;

    public MonumentMenu(int windowId, Inventory playerInventory, BlockPos monumentPos) {
        super(MONUMENT_MENU.get(), windowId);
        this.monumentPos = monumentPos;
        this.chunkX = monumentPos.getX() >> 4;
        this.chunkZ = monumentPos.getZ() >> 4;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide) {
            return true;
        }

        ChunkClaimManager.ClaimData claim = ChunkClaimManager.getClaim(player.level(), chunkX, chunkZ);
        return claim != null && claim.ownerUUID.equals(player.getUUID());
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
    }

    public BlockPos getMonumentPos() {
        return monumentPos;
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public Set<UUID> getAllowedPlayers(Player player) {
        return ChunkClaimManager.getAllowedPlayers(player.level(), chunkX, chunkZ);
    }

    public static class MonumentMenuProvider implements MenuProvider {
        private final BlockPos pos;

        public MonumentMenuProvider(BlockPos pos) {
            this.pos = pos;
        }

        @Override
        public Component getDisplayName() {
            return Component.literal("Monument Management");
        }

        @Nullable
        @Override
        public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
            return new MonumentMenu(containerId, playerInventory, pos);
        }
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}