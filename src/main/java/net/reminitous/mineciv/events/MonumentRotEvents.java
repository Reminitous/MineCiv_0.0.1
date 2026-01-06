package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.territory.TerritoryManager;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class MonumentRotEvents {

    private MonumentRotEvents() {}

    // 7 days real time
    private static final long ROT_AFTER_MS = 7L * 24L * 60L * 60L * 1000L;

    // How often to scan (ticks). 1200 = 60 seconds.
    private static final int SCAN_EVERY_TICKS = 1200;

    private static int tickCounter = 0;

    /**
     * Update lastActive when ANY member logs in.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        CivSavedData data = CivSavedData.get(server);
        UUID civId = data.getPlayersCiv(player.getUUID());
        if (civId == null) return;

        Civilization civ = data.getCiv(civId);
        if (civ == null) return;

        civ.setLastActiveEpochMs(System.currentTimeMillis());
        data.putCiv(civ);
    }

    /**
     * Periodic scan: remove civs that have been inactive for 7 days.
     */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        tickCounter++;
        if (tickCounter < SCAN_EVERY_TICKS) return;
        tickCounter = 0;

        MinecraftServer server = e.getServer();
        CivSavedData data = CivSavedData.get(server);

        long now = System.currentTimeMillis();
        List<UUID> toRot = new ArrayList<>();

        for (Civilization civ : data.civs().values()) {
            long last = civ.lastActiveEpochMs();
            if (last <= 0) continue;

            if (now - last >= ROT_AFTER_MS) {
                toRot.add(civ.id());
            }
        }

        if (toRot.isEmpty()) return;

        // Overworld is the authority for claims
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        for (UUID civId : toRot) {
            Civilization civ = data.getCiv(civId);
            if (civ == null) continue;

            rotCiv(server, overworld, data, civ);
        }
    }

    /* ---------------- Core rot logic ---------------- */

    private static void rotCiv(MinecraftServer server, ServerLevel overworld, CivSavedData data, Civilization civ) {
        UUID civId = civ.id();

        // 1) Unclaim all chunks immediately (structures remain)
        TerritoryManager.unclaimAllChunks(overworld, civId);

        // 2) Clear player->civ mapping for members
        for (UUID member : civ.members()) {
            data.setPlayersCiv(member, null);
        }

        // 3) Remove monument if chunk is loaded (best-effort; no force-load)
        removeMonumentIfLoaded(server, civ);

        // 4) Remove civ record
        data.removeCiv(civId);

        // Optional: broadcast
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("A civilization has rotted from inactivity: " +
                        (civ.name() == null || civ.name().isBlank() ? civId.toString() : civ.name())),
                false
        );
    }

    private static void removeMonumentIfLoaded(MinecraftServer server, Civilization civ) {
        BlockPos pos = civ.monumentPos();
        if (pos == null) return;

        ServerLevel level = levelForCivMonument(server, civ);
        if (level == null) return;

        // Only remove if chunk is loaded (don't force-load)
        if (!level.hasChunkAt(pos)) return;

        // Remove the monument block; structures remain
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
    }

    private static ServerLevel levelForCivMonument(MinecraftServer server, Civilization civ) {
        String dim = civ.monumentDimId();
        if (dim == null || dim.isBlank()) {
            return server.getLevel(Level.OVERWORLD);
        }

        // dim string like "minecraft:overworld"
        ResourceLocation rl = ResourceLocation.tryParse(dim);
        if (rl == null) return server.getLevel(Level.OVERWORLD);

        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, rl);
        return server.getLevel(key);
    }
}
