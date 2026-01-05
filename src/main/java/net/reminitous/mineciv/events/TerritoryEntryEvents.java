package net.reminitous.mineciv.events;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.reminitous.mineciv.MineCiv;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = MineCiv.MOD_ID)
public final class TerritoryEntryEvents {

    private TerritoryEntryEvents() {}

    // Tracks last seen chunk-owner civ per player (null = wilderness)
    private static final Map<UUID, UUID> LAST_OWNER = new HashMap<>();
    private static final Map<UUID, Long> LAST_CHUNK = new HashMap<>();

    // Slight tints
    private static final TextColor ALLY_GREEN = TextColor.fromRgb(0x55FF55);
    private static final TextColor ENEMY_RED  = TextColor.fromRgb(0xFF5555);
    private static final TextColor YOUR_AQUA  = TextColor.fromRgb(0x55FFFF);

    private static MutableComponent buildEnteringMessage(ServerPlayer player, CivSavedData data, UUID owner) {
        // Base territory name
        String territoryName;
        if (owner == null) {
            territoryName = "Wilderness";
        } else {
            Civilization civ = data.getCiv(owner);
            territoryName = (civ != null && civ.name() != null && !civ.name().isBlank())
                    ? civ.name()
                    : "Unknown Civilization";
        }

        MutableComponent msg = Component.literal("Entering: " + territoryName);

        // Colored suffix
        if (owner == null) {
            return msg; // no suffix for wilderness
        }

        Optional<Civilization> pc = CivilizationManager.findPlayerCiv(player.serverLevel(), player.getUUID());
        if (pc.isPresent() && pc.get().id().equals(owner)) {
            msg.append(Component.literal(" (Your land)").withStyle(s -> s.withColor(YOUR_AQUA)));
        } else if (pc.isPresent() && CivilizationManager.areAllies(player.serverLevel(), pc.get().id(), owner)) {
            msg.append(Component.literal(" (Ally)").withStyle(s -> s.withColor(ALLY_GREEN)));
        } else if (pc.isPresent()) {
            msg.append(Component.literal(" (Enemy land)").withStyle(s -> s.withColor(ENEMY_RED)));
        }

        return msg;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer player)) return;

        CivSavedData data = CivSavedData.get(player.getServer());
        ChunkPos cp = new ChunkPos(player.blockPosition());
        long chunkLong = cp.toLong();

        UUID owner = data.getChunkOwner(chunkLong); // null = wilderness

        // Seed state so tick logic behaves correctly
        LAST_CHUNK.put(player.getUUID(), chunkLong);
        LAST_OWNER.put(player.getUUID(), owner);

        // Show initial message ONCE
        player.displayClientMessage(buildEnteringMessage(player, data, owner), true);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer player)) return;

        // Only run every 10 ticks (~0.5s)
        if ((player.tickCount % 10) != 0) return;

        ChunkPos cp = new ChunkPos(player.blockPosition());
        long chunkLong = cp.toLong();

        Long lastChunk = LAST_CHUNK.get(player.getUUID());
        if (lastChunk != null && lastChunk == chunkLong) return;

        LAST_CHUNK.put(player.getUUID(), chunkLong);

        CivSavedData data = CivSavedData.get(player.getServer());
        UUID newOwner = data.getChunkOwner(chunkLong); // null = wilderness

        UUID oldOwner = LAST_OWNER.get(player.getUUID());

        // If owner didn't change, don't announce anything.
        if (oldOwner != null && oldOwner.equals(newOwner)) return;

        // wilderness -> wilderness: no spam
        if (oldOwner == null && newOwner == null) return;

        // Update owner and announce transition
        LAST_OWNER.put(player.getUUID(), newOwner);

        player.displayClientMessage(buildEnteringMessage(player, data, newOwner), true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        LAST_OWNER.remove(id);
        LAST_CHUNK.remove(id);
    }
}
