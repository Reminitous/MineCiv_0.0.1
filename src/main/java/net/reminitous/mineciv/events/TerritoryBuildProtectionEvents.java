package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.minecraftforge.event.entity.player.PlayerInteractEvent;
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
public final class TerritoryBuildProtectionEvents {

    private TerritoryBuildProtectionEvents() {}

    private static final String NBT_LAST_DENY_TICK = "MineCiv_LastBuildDenyTick";

    @SubscribeEvent
    public static void onBlockBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getPlayer() instanceof ServerPlayer player)) return;

        BlockPos pos = e.getPos();

        UUID ownerCivId = TerritoryManager.getOwnerCivId(level, pos);
        if (ownerCivId == null) return; // wilderness ok

        UUID playerCivId = getPlayerCivId(level, player);

        if (isAllowed(level, playerCivId, ownerCivId)) return;

        e.setCanceled(true);
        denyMessage(player, "You cannot break blocks in this territory.");
    }

    @SubscribeEvent
    public static void onBlockPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        BlockPos pos = e.getPos();

        UUID ownerCivId = TerritoryManager.getOwnerCivId(level, pos);
        if (ownerCivId == null) return; // wilderness ok

        UUID playerCivId = getPlayerCivId(level, player);

        if (isAllowed(level, playerCivId, ownerCivId)) return;

        e.setCanceled(true);
        denyMessage(player, "You cannot place blocks in this territory.");
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        // We only care about actions that modify the world via right click (buckets etc.)
        ItemStack held = e.getItemStack();
        if (!(held.getItem() instanceof BucketItem)) return;

        BlockPos pos = e.getPos().relative(e.getFace());

        UUID ownerCivId = TerritoryManager.getOwnerCivId(level, pos);
        if (ownerCivId == null) return; // wilderness ok

        UUID playerCivId = getPlayerCivId(level, player);

        if (isAllowed(level, playerCivId, ownerCivId)) return;

        e.setCanceled(true);
        denyMessage(player, "You cannot modify blocks in this territory.");
    }

    /* ---------------- Helpers ---------------- */

    private static UUID getPlayerCivId(ServerLevel level, ServerPlayer player) {
        Optional<Civilization> civOpt = CivilizationManager.findPlayerCiv(level, player.getUUID());
        return civOpt.map(Civilization::id).orElse(null);
    }

    private static boolean isAllowed(ServerLevel level, UUID playerCivId, UUID ownerCivId) {
        if (playerCivId == null) return false;
        if (playerCivId.equals(ownerCivId)) return true;

        // Allow allies to build/break too (change to "return false;" if you want allies blocked)
        return false;
    }

    private static void denyMessage(ServerPlayer player, String msg) {
        long nowTick = player.server.getTickCount();
        long last = player.getPersistentData().getLong(NBT_LAST_DENY_TICK);

        // No spam: at most once per second
        if (nowTick - last < 20) return;

        player.getPersistentData().putLong(NBT_LAST_DENY_TICK, nowTick);
        player.sendSystemMessage(Component.literal(msg));
    }
}
