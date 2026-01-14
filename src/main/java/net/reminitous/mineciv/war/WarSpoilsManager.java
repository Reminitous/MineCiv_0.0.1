package net.reminitous.mineciv.war;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

import net.minecraftforge.common.capabilities.ForgeCapabilities;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class WarSpoilsManager {

    // Tuning knobs (v1)
    public static final int SPOILS_BPS = 2000;             // 20% of value removed & transferred
    public static final long TAX_DURATION_MS = 6L * 60L * 60L * 1000L; // 6 hours production tax (stored, not enforced yet)
    public static final int TAX_BPS = 2000;                // 20% production tax

    private WarSpoilsManager() {}

    public static void applySpoilsAndCooldowns(ServerLevel level, UUID winnerCivId, UUID loserCivId) {
        if (winnerCivId == null || loserCivId == null) return;

        CivSavedData civData = CivSavedData.get(level.getServer());
        Civilization winner = civData.getCiv(winnerCivId);
        Civilization loser = civData.getCiv(loserCivId);
        if (winner == null || loser == null) return;

        // 1) Remove items from loser up to target value and collect them
        long loserValueNow = snapshotCivStorageValue(level, loser);
        long targetValue = (loserValueNow * SPOILS_BPS) / 10_000L;
        if (targetValue <= 0) targetValue = 0;

        List<ItemStack> stolen = new ArrayList<>();
        long takenValue = takeItemsUpToValue(level, loser, targetValue, stolen);

        // 2) Give items to winner (deposit or drop near monument)
        giveItemsToWinner(level, winner, stolen);

        // 3) Cooldowns + tax status
        long now = System.currentTimeMillis();

        WarStatusSavedData status = WarStatusSavedData.get(level.getServer());

        // Loser grace (12h)
        status.setGrace(loserCivId, now + 12L * 60L * 60L * 1000L);

        // Winner cannot rematch same loser for 48h (pair cooldown)
        status.setRematch(winnerCivId, loserCivId, now + 48L * 60L * 60L * 1000L);

        // Production tax status stored (enforcement is next step)
        status.setTax(loserCivId, winnerCivId, TAX_BPS, TAX_DURATION_MS);

        // Optional: you can message later; WarEndManager already broadcasts win/loss.
        // This method is intentionally silent.
    }

    /* ---------------- Value + scanning ---------------- */

    private static long snapshotCivStorageValue(ServerLevel level, Civilization civ) {
        long sum = 0L;

        for (long chunkLong : civ.claimedChunks()) {
            ChunkPos cp = new ChunkPos(chunkLong);
            LevelChunk chunk = level.getChunk(cp.x, cp.z);

            for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                BlockEntity be = chunk.getBlockEntity(bePos);
                if (be == null) continue;

                var cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
                if (!cap.isPresent()) continue;

                var inv = cap.orElse(null);
                if (inv == null) continue;

                for (int i = 0; i < inv.getSlots(); i++) {
                    ItemStack st = inv.getStackInSlot(i);
                    if (st.isEmpty()) continue;
                    sum += stackValue(st);
                }
            }
        }

        return sum;
    }

    private static long takeItemsUpToValue(ServerLevel level, Civilization loser, long targetValue, List<ItemStack> out) {
        if (targetValue <= 0) return 0L;

        long taken = 0L;

        for (long chunkLong : loser.claimedChunks()) {
            if (taken >= targetValue) break;

            ChunkPos cp = new ChunkPos(chunkLong);
            LevelChunk chunk = level.getChunk(cp.x, cp.z);

            for (BlockPos bePos : chunk.getBlockEntitiesPos()) {
                if (taken >= targetValue) break;

                BlockEntity be = chunk.getBlockEntity(bePos);
                if (be == null) continue;

                var cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
                if (!cap.isPresent()) continue;

                var inv = cap.orElse(null);
                if (inv == null) continue;

                // Greedy: pull from last slot down (slightly reduces churn)
                for (int slot = inv.getSlots() - 1; slot >= 0; slot--) {
                    if (taken >= targetValue) break;

                    ItemStack st = inv.getStackInSlot(slot);
                    if (st.isEmpty()) continue;

                    // take whole stack (v1)
                    ItemStack extracted = inv.extractItem(slot, st.getCount(), false);
                    if (extracted.isEmpty()) continue;

                    out.add(extracted);
                    taken += stackValue(extracted);
                }
            }
        }

        return taken;
    }

    private static long stackValue(ItemStack st) {
        int count = st.getCount();
        Rarity r = st.getRarity();
        int w = switch (r) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 4;
            case EPIC -> 8;
        };
        return (long) count * w;
    }

    /* ---------------- Giving items ---------------- */

    private static void giveItemsToWinner(ServerLevel level, Civilization winner, List<ItemStack> items) {
        if (items == null || items.isEmpty()) return;

        BlockPos monument = winner.monumentPos();
        if (monument == null) {
            // No monument? drop at 0,0? We just drop at spawn
            BlockPos spawn = level.getSharedSpawnPos();
            dropAll(level, spawn, items);
            return;
        }

        // Try to find a nearby container within radius 3 (chest/barrel/etc via ITEM_HANDLER)
        BlockPos containerPos = findNearbyContainer(level, monument, 3);

        if (containerPos == null) {
            dropAll(level, monument, items);
            return;
        }

        BlockEntity be = level.getBlockEntity(containerPos);
        if (be == null) {
            dropAll(level, monument, items);
            return;
        }

        var cap = be.getCapability(ForgeCapabilities.ITEM_HANDLER, null);
        if (!cap.isPresent()) {
            dropAll(level, monument, items);
            return;
        }

        var inv = cap.orElse(null);
        if (inv == null) {
            dropAll(level, monument, items);
            return;
        }

        // Insert stacks, leftovers drop
        List<ItemStack> leftovers = new ArrayList<>();

        for (ItemStack st : items) {
            ItemStack remaining = st.copy();

            for (int slot = 0; slot < inv.getSlots(); slot++) {
                if (remaining.isEmpty()) break;
                remaining = inv.insertItem(slot, remaining, false);
            }

            if (!remaining.isEmpty()) leftovers.add(remaining);
        }

        if (!leftovers.isEmpty()) dropAll(level, monument, leftovers);
    }

    private static BlockPos findNearbyContainer(ServerLevel level, BlockPos center, int radius) {
        int r = Math.max(1, radius);

        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos p = center.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(p);
                    if (be == null) continue;
                    if (be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).isPresent()) {
                        return p;
                    }
                }
            }
        }
        return null;
    }

    private static void dropAll(ServerLevel level, BlockPos pos, List<ItemStack> items) {
        Vec3 drop = new Vec3(pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5);
        for (ItemStack st : items) {
            if (st == null || st.isEmpty()) continue;
            level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level, drop.x, drop.y, drop.z, st));
        }
    }
}
