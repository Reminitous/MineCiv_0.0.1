package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.territory.TerritoryManager;

import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class TerritoryContainerAccessEvents {

    private TerritoryContainerAccessEvents() {}

    private static final String NBT_LAST_DENY_TICK = "MineCiv_LastDenyTick";

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = e.getPos();

        // Only enforce in claimed territory
        UUID ownerCivId = TerritoryManager.getOwnerCivId(level, pos);
        if (ownerCivId == null) return;

        // Only block "storage-ish" blocks (containers/furnaces/etc.)
        if (!isStorageBlock(level, pos)) return;

        UUID playerCivId = getPlayerCivId(level, player);
        if (isAllowed(level, playerCivId, ownerCivId)) return;

        // Deny access
        e.setCanceled(true);
        denyMessage(player, "You cannot access storage in this territory.");
    }

    @SubscribeEvent
    public static void onRightClickEntity(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        Entity target = e.getTarget();
        if (!(target instanceof AbstractMinecartContainer)) return;

        // Territory check based on entity position
        UUID ownerCivId = TerritoryManager.getOwnerCivId(level, target.blockPosition());
        if (ownerCivId == null) return;

        UUID playerCivId = getPlayerCivId(level, player);
        if (isAllowed(level, playerCivId, ownerCivId)) return;

        e.setCanceled(true);
        denyMessage(player, "You cannot access storage in this territory.");
    }

    /* ---------------- Helpers ---------------- */

    private static boolean isStorageBlock(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;

        // Furnaces / smokers / blast furnaces
        if (be instanceof AbstractFurnaceBlockEntity) return true;

        // Any block entity that exposes item storage (chests, barrels, shulkers, etc.)
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent();
    }

    private static UUID getPlayerCivId(ServerLevel level, ServerPlayer player) {
        Optional<Civilization> civOpt = CivilizationManager.findPlayerCiv(level, player.getUUID());
        return civOpt.map(Civilization::id).orElse(null);
    }

    private static boolean isAllowed(ServerLevel level, UUID playerCivId, UUID ownerCivId) {
        if (playerCivId == null) return false;                 // outsiders blocked
        if (playerCivId.equals(ownerCivId)) return true;       // same civ ok

        // Allies allowed (you asked for allies as a major system)
        return CivilizationManager.areAllies(level, playerCivId, ownerCivId);
    }

    private static void denyMessage(ServerPlayer player, String msg) {
        long nowTick = player.server.getTickCount();
        long last = player.getPersistentData().getLong(NBT_LAST_DENY_TICK);

        // Prevent chat spam: at most once per second
        if (nowTick - last < 20) return;

        player.getPersistentData().putLong(NBT_LAST_DENY_TICK, nowTick);
        player.sendSystemMessage(Component.literal(msg));
    }
}
