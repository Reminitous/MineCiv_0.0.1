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
import net.minecraftforge.event.entity.player.AttackEntityEvent;
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
public final class TerritoryStorageBreakEvents {

    private TerritoryStorageBreakEvents() {}

    private static final String NBT_LAST_STORAGE_BREAK_DENY_TICK = "MineCiv_LastStorageBreakDenyTick";

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos pos = e.getPos();

        // Only enforce for storage blocks
        if (!isStorageBlock(level, pos)) return;

        UUID ownerCivId = TerritoryManager.getOwnerCivId(level, pos);
        if (ownerCivId == null) return; // wilderness: allow

        UUID playerCivId = getPlayerCivId(level, player);
        if (playerCivId != null && playerCivId.equals(ownerCivId)) return; // owner civ ok

        // Deny
        e.setCanceled(true);
        denyMessage(player, "You cannot break storage in this territory.");
    }

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent e) {
        // Covers minecart containers (chest minecart, hopper minecart, etc.)
        if (!(e.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;

        Entity target = e.getTarget();
        if (!(target instanceof AbstractMinecartContainer)) return;

        UUID ownerCivId = TerritoryManager.getOwnerCivId(level, target.blockPosition());
        if (ownerCivId == null) return; // wilderness: allow

        UUID playerCivId = getPlayerCivId(level, player);
        if (playerCivId != null && playerCivId.equals(ownerCivId)) return; // owner civ ok

        e.setCanceled(true);
        denyMessage(player, "You cannot destroy storage in this territory.");
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
        long last = player.getPersistentData().getLong(NBT_LAST_STORAGE_BREAK_DENY_TICK);

        if (nowTick - last < 20) return; // 1 msg/sec
        player.getPersistentData().putLong(NBT_LAST_STORAGE_BREAK_DENY_TICK, nowTick);

        player.sendSystemMessage(Component.literal(msg));
    }
}
