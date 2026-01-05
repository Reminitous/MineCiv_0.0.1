package net.reminitous.mineciv.events;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import net.minecraftforge.event.TickEvent;
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
public final class CivRotEvents {

    private CivRotEvents() {}

    private static final long ROT_MS = 7L * 24L * 60L * 60L * 1000L; // 7 days

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;

        MinecraftServer server = e.getServer();
        if ((server.getTickCount() % (20 * 60 * 5)) != 0) return; // every 5 minutes

        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        CivSavedData data = CivSavedData.get(server);
        long now = System.currentTimeMillis();

        List<UUID> toRemove = new ArrayList<>();

        for (Civilization civ : data.civs().values()) {
            long last = civ.lastActiveEpochMs();
            if (last <= 0) continue;

            if (now - last < ROT_MS) continue;

            // ROT this civ
            MineCiv.LOGGER.info("MineCiv: Rotting civ {} (inactive > 7 days)", civ.id());

            // 1) Remove NPC entities (only those currently loaded)
            for (UUID npcId : civ.npcIds()) {
                for (ServerLevel lvl : server.getAllLevels()) {
                    var ent = lvl.getEntity(npcId);
                    if (ent != null) {
                        ent.discard();
                    }
                }
            }

            // 2) Unclaim all chunks
            for (long chunkLong : civ.claimedChunks()) {
                TerritoryManager.unclaimChunk(overworld, civ.id(), new net.minecraft.world.level.ChunkPos(chunkLong));
            }

            // 3) Remove monument block if loaded in its dimension
            BlockPos mp = civ.monumentPos();
            String dim = civ.monumentDimId();

            if (mp != null && dim != null) {
                for (ServerLevel lvl : server.getAllLevels()) {
                    if (!lvl.dimension().location().toString().equals(dim)) continue;
                    if (!lvl.hasChunkAt(mp)) continue;

                    // Remove the monument block (structure remains as you wanted)
                    lvl.setBlock(mp, Blocks.AIR.defaultBlockState(), 3);
                }
            }

            toRemove.add(civ.id());
        }

        // 4) Finally remove civ records
        for (UUID civId : toRemove) {
            data.removeCiv(civId);
        }
    }
}
