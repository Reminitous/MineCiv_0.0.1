package net.reminitous.mineciv.war;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;

import java.util.UUID;

public final class WarTaxUtil {

    private WarTaxUtil() {}

    /** Returns true if (loser civ) currently owes production tax, and if so fills out winner/bps. */
    public static TaxInfo getActiveTax(ServerLevel level, UUID producerCivId) {
        if (producerCivId == null) return TaxInfo.none();

        // You said you already want spoils+cooldowns; this data holder is the clean place.
        // REQUIRED: implement these methods in your WarStatusSavedData (or rename them here to match yours).
        WarStatusSavedData status = WarStatusSavedData.get(level.getServer());

        long now = System.currentTimeMillis();
        UUID winner = status.getTaxWinnerCivId(producerCivId);
        int bps = status.getTaxBps(producerCivId); // 2000 = 20%
        long until = status.getTaxUntilMs(producerCivId);

        if (winner == null) return TaxInfo.none();
        if (bps <= 0) return TaxInfo.none();

        if (until > 0 && now > until) {
            // expire automatically
            status.clearTax(producerCivId);
            return TaxInfo.none();
        }

        return new TaxInfo(true, winner, bps, until);
    }

    /** Apply bps tax to a stack count. Returns how many items should be transferred. */
    public static int computeTaxCount(int count, int bps) {
        if (count <= 0) return 0;
        if (bps <= 0) return 0;
        long taxed = (long) count * (long) bps / 10_000L;
        if (taxed <= 0) return 0;
        if (taxed >= count) return count;
        return (int) taxed;
    }

    /** Drops the taxed items at the winner monument (simple v1). */
    public static void transferToWinner(ServerLevel level, UUID winnerCivId, ItemStack taxedStack) {
        if (taxedStack == null || taxedStack.isEmpty()) return;
        if (winnerCivId == null) return;

        CivSavedData civData = CivSavedData.get(level.getServer());
        Civilization winner = civData.getCiv(winnerCivId);
        if (winner == null) return;

        BlockPos mPos = winner.monumentPos();
        if (mPos == null) return;

        // Always use overworld for drops (your project uses overworld as authority a lot)
        ServerLevel overworld = level.getServer().getLevel(net.minecraft.world.level.Level.OVERWORLD);
        if (overworld == null) overworld = level;

        BlockPos drop = mPos.above();
        ItemEntity ent = new ItemEntity(overworld, drop.getX() + 0.5, drop.getY() + 0.5, drop.getZ() + 0.5, taxedStack);
        ent.setDefaultPickUpDelay();
        overworld.addFreshEntity(ent);
    }

    /** Simple record to return current tax status. */
    public record TaxInfo(boolean active, UUID winnerCivId, int bps, long untilMs) {
        public static TaxInfo none() { return new TaxInfo(false, null, 0, 0L); }
    }
}
