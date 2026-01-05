package net.reminitous.mineciv.events;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
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
public final class TerritoryBossbarEvents {

    private TerritoryBossbarEvents() {}

    private static final Map<UUID, Long> LAST_CHUNK = new HashMap<>();
    private static final Map<UUID, UUID> LAST_OWNER = new HashMap<>();
    private static final Map<UUID, ServerBossEvent> BARS = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        if (!(e.player instanceof ServerPlayer player)) return;

        // Update twice per second
        if ((player.tickCount % 10) != 0) return;

        ChunkPos cp = new ChunkPos(player.blockPosition());
        long chunkLong = cp.toLong();

        Long lastChunk = LAST_CHUNK.get(player.getUUID());
        if (lastChunk != null && lastChunk == chunkLong) return;

        LAST_CHUNK.put(player.getUUID(), chunkLong);

        CivSavedData data = CivSavedData.get(player.getServer()); // global authority
        UUID newOwner = data.getChunkOwner(chunkLong);

        UUID oldOwner = LAST_OWNER.get(player.getUUID());
        if (oldOwner != null && oldOwner.equals(newOwner)) return; // owner unchanged

        LAST_OWNER.put(player.getUUID(), newOwner);

        // Show/hide + update bossbar
        if (newOwner == null) {
            hideBar(player);
            return;
        }

        Civilization civ = data.getCiv(newOwner);
        String territoryName = (civ != null && civ.name() != null && !civ.name().isBlank())
                ? civ.name()
                : "Unknown Civilization";

        // Relationship suffix
        String suffix = "";
        Optional<Civilization> pc = CivilizationManager.findPlayerCiv(player.serverLevel(), player.getUUID());
        if (pc.isPresent() && pc.get().id().equals(newOwner)) {
            suffix = " (Your land)";
        } else if (pc.isPresent() && CivilizationManager.areAllies(player.serverLevel(), pc.get().id(), newOwner)) {
            suffix = " (Ally)";
        } else if (pc.isPresent()) {
            suffix = " (Enemy land)";
        }

        showOrUpdateBar(player, "Territory: " + territoryName + suffix);
    }

    private static void showOrUpdateBar(ServerPlayer player, String title) {
        ServerBossEvent bar = BARS.get(player.getUUID());

        BossEvent.BossBarColor color = BossEvent.BossBarColor.BLUE;
        if (title.contains("Wilderness")) {
            color = BossEvent.BossBarColor.GREEN;
        } else if (title.contains("Enemy")) {
            color = BossEvent.BossBarColor.RED;
        } else if (title.contains("Ally")) {
            color = BossEvent.BossBarColor.PURPLE;
        }

        if (bar == null) {
            bar = new ServerBossEvent(
                    Component.literal(title),
                    color,
                    BossEvent.BossBarOverlay.PROGRESS
            );
            bar.setProgress(1.0f);
            bar.addPlayer(player);
            bar.setVisible(true);
            BARS.put(player.getUUID(), bar);
        } else {
            bar.setName(Component.literal(title));
            bar.setColor(color);
            bar.setProgress(1.0f);
            bar.setVisible(true);
            bar.addPlayer(player);
        }
    }

    private static void hideBar(ServerPlayer player) {
        ServerBossEvent bar = BARS.get(player.getUUID());
        if (bar != null) {
            bar.setVisible(false);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        UUID id = e.getEntity().getUUID();
        LAST_CHUNK.remove(id);
        LAST_OWNER.remove(id);

        ServerBossEvent bar = BARS.remove(id);
        if (bar != null && e.getEntity() instanceof ServerPlayer sp) {
            bar.removePlayer(sp);
        }
    }
}
