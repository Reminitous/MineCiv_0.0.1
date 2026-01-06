package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;

import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.territory.TerritoryManager;

import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class TerritoryStoragePlacementEvents {

    private TerritoryStoragePlacementEvents() {}

    private static final String NBT_LAST_STORAGE_PLACE_DENY_TICK = "MineCiv_LastStoragePlaceDenyTick";

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        // Only check after placement snapshot exists (block entity should exist now)
        BlockPos pos = e.getPos();

        // If not a storage block, ignore
        if (!isStorageBlock(level, pos)) return;

        // Wilderness: allowed
        UUID ownerCivId = TerritoryManager.getOwnerCivId(level, pos);
        if (ownerCivId == null) return;

        // Claimed land: only owner civ may place storage
        UUID playerCivId = getPlayerCivId(level, player);
        if (playerCivId != null && playerCivId.equals(ownerCivId)) return;

        // Deny
        e.setCanceled(true);
        denyMessage(player, "You may only place storage inside your own civilization territory.");
    }

    @SubscribeEvent
    public static void onMultiPlace(BlockEvent.EntityMultiPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        UUID playerCivId = getPlayerCivId(level, player);

        // Check every placed block snapshot
        for (var snapshot : e.getReplacedBlockSnapshots()) {
            BlockPos pos = snapshot.getPos();

            if (!isStorageBlock(level, pos)) continue;

            UUID ownerCivId = TerritoryManager.getOwnerCivId(level, pos);
            if (ownerCivId == null) continue; // wilderness: ok

            if (playerCivId != null && playerCivId.equals(ownerCivId)) continue;

            e.setCanceled(true);
            denyMessage(player, "You may only place storage inside your own civilization territory.");
            return;
        }
    }

    /* ---------------- Helpers ---------------- */

    private static boolean isStorageBlock(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null) return false;

        // Furnaces / smokers / blast furnaces
        if (be instanceof AbstractFurnaceBlockEntity) return true;

        // Any block entity that exposes item storage (chests, barrels, shulkers, hoppers, etc.)
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent();
    }

    private static UUID getPlayerCivId(ServerLevel level, ServerPlayer player) {
        Optional<Civilization> civOpt = CivilizationManager.findPlayerCiv(level, player.getUUID());
        return civOpt.map(Civilization::id).orElse(null);
    }

    private static void denyMessage(ServerPlayer player, String msg) {
        long nowTick = player.server.getTickCount();
        long last = player.getPersistentData().getLong(NBT_LAST_STORAGE_PLACE_DENY_TICK);

        // Prevent spam: at most once per second
        if (nowTick - last < 20) return;

        player.getPersistentData().putLong(NBT_LAST_STORAGE_PLACE_DENY_TICK, nowTick);
        player.sendSystemMessage(Component.literal(msg));
    }
}
