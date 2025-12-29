package net.reminitous.mineciv.screen;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.block.ChunkClaimManager;
import net.reminitous.mineciv.block.entity.MonumentBlockEntity;
import net.reminitous.mineciv.civ.CivilizationType;

import javax.annotation.Nullable;
import java.util.Set;
import java.util.UUID;

public class MonumentMenu extends AbstractContainerMenu {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MineCiv.MOD_ID);

    public static final RegistryObject<MenuType<MonumentMenu>> MONUMENT_MENU = MENUS.register("monument_menu",
            () -> IForgeMenuType.create((windowId, inv, data) -> {
                BlockPos pos = data.readBlockPos();
                return new MonumentMenu(windowId, inv, pos);
            }));

    private final Level level;
    private final BlockPos monumentPos;
    private final int chunkX;
    private final int chunkZ;

    public MonumentMenu(int windowId, Inventory playerInventory, BlockPos monumentPos) {
        super(MONUMENT_MENU.get(), windowId);
        this.level = playerInventory.player.level();
        this.monumentPos = monumentPos;
        this.chunkX = monumentPos.getX() >> 4;
        this.chunkZ = monumentPos.getZ() >> 4;
    }

    @Override
    public net.minecraft.world.item.ItemStack quickMoveStack(Player player, int index) {
        return net.minecraft.world.item.ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.level().isClientSide) return true;
        ChunkClaimManager.ClaimData claim = ChunkClaimManager.getClaim(player.level(), chunkX, chunkZ);
        return claim != null && claim.ownerUUID.equals(player.getUUID());
    }

    // ✅ This is called on the SERVER when the client clicks a button in the screen.
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (level.isClientSide) return false;

        // Only allow owner to set civ
        ChunkClaimManager.ClaimData claim = ChunkClaimManager.getClaim(level, chunkX, chunkZ);
        if (claim == null || !claim.ownerUUID.equals(player.getUUID())) return false;

        if (!(level.getBlockEntity(monumentPos) instanceof MonumentBlockEntity be)) return false;

        CivilizationType type = CivilizationType.fromButtonId(id);
        be.setCivType(type);
        return true;
    }

    public CivilizationType getCivilizationType() {
        if (level.getBlockEntity(monumentPos) instanceof MonumentBlockEntity be) {
            return be.getCivType();
        }
        return CivilizationType.FARMER;
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

    public static class MonumentMenuProvider implements net.minecraft.world.MenuProvider {
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
