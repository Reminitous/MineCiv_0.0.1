package net.reminitous.mineciv.monument;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.civ.CivClassType;
import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.S2C_OpenCreateCivScreenPacket;
import net.reminitous.mineciv.net.pkt.S2C_OpenManageCivScreenPacket;
import net.reminitous.mineciv.net.pkt.S2C_OpenDisbandConfirmScreenPacket;
import net.reminitous.mineciv.territory.TerritoryManager;
import net.reminitous.mineciv.war.WarSavedData;
import net.reminitous.mineciv.war.WarState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class MonumentBlock extends BaseEntityBlock {

    public static final MapCodec<MonumentBlock> CODEC = simpleCodec(MonumentBlock::new);

    public MonumentBlock(Properties props) {
        super(props);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonumentBlockEntity(pos, state);
    }

    /** Right-click with empty hand */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return handleRightClick(level, pos, player);
    }

    /** Right-click while holding items (1.21.x safe) */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hit
    ) {
        InteractionResult res = handleRightClick(level, pos, player);
        return res.consumesAction()
                ? ItemInteractionResult.SUCCESS
                : ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /* ================= CORE ================= */

    private static InteractionResult handleRightClick(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel sLevel)) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sPlayer)) return InteractionResult.PASS;

        BlockEntity be = sLevel.getBlockEntity(pos);
        if (!(be instanceof MonumentBlockEntity monumentBE)) return InteractionResult.PASS;

        /* =====================================================
         * SHIFT + RIGHT CLICK → DISBAND CONFIRM (LEADER ONLY)
         * ===================================================== */
        if (sPlayer.isShiftKeyDown() && monumentBE.isBound()) {
            UUID civId = monumentBE.getCivId();
            if (civId == null) return InteractionResult.SUCCESS;

            CivSavedData data = CivSavedData.get(sLevel.getServer());
            Civilization civ = data.getCiv(civId);
            if (civ == null) return InteractionResult.SUCCESS;

            if (civ.leader() != null && civ.leader().equals(sPlayer.getUUID())) {
                String civName = (civ.name() == null || civ.name().isBlank())
                        ? "your civilization"
                        : civ.name();

                Network.CH.send(
                        new S2C_OpenDisbandConfirmScreenPacket(pos, civId, civName),
                        PacketDistributor.PLAYER.with(sPlayer)
                );
            } else {
                sPlayer.sendSystemMessage(Component.literal("Only the civilization leader may disband."));
            }
            return InteractionResult.SUCCESS;
        }

        /* ================= MONUMENT ROT CLEANUP ================= */
        if (monumentBE.isBound()) {
            UUID civId = monumentBE.getCivId();
            CivSavedData data = CivSavedData.get(sLevel.getServer());

            if (civId == null || data.getCiv(civId) == null) {
                sLevel.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                sPlayer.sendSystemMessage(Component.literal("This monument has crumbled due to inactivity."));
                return InteractionResult.SUCCESS;
            }
        }

        /* ================= CREATE CIV ================= */
        if (!monumentBE.isBound()) {
            Network.CH.send(
                    new S2C_OpenCreateCivScreenPacket(pos),
                    PacketDistributor.PLAYER.with(sPlayer)
            );
            return InteractionResult.SUCCESS;
        }

        /* ================= MANAGE CIV ================= */
        UUID civId = monumentBE.getCivId();
        if (civId == null) return InteractionResult.SUCCESS;

        var server = sLevel.getServer();
        CivSavedData data = CivSavedData.get(server);
        Civilization civ = data.getCiv(civId);
        if (civ == null) return InteractionResult.SUCCESS;

        boolean isLeader = civ.leader() != null && civ.leader().equals(sPlayer.getUUID());
        List<UUID> members = new ArrayList<>(civ.members());

        List<UUID> pendingInvites = new ArrayList<>();
        for (Map.Entry<UUID, UUID> e : data.pendingInvites().entrySet()) {
            if (civId.equals(e.getValue())) pendingInvites.add(e.getKey());
        }

        int claimedChunks = civ.claimedChunks().size();
        int maxChunks = TerritoryManager.MAX_CHUNKS;

        /* ================= PENDING WAR SNAPSHOT ================= */
        boolean hasPendingWar = false;
        UUID pendingWarId = null;
        String pendingPhase = "";
        UUID pendingOpponentCivId = new UUID(0L, 0L);
        String pendingOpponentName = "";
        long pendingStartsAtMs = 0L;
        int pendingPrepMinutes = 0;

        WarSavedData warData = WarSavedData.get(server);
        UUID pw = warData.getPendingWarId(civ.id());
        if (pw != null) {
            WarState w = warData.getWar(pw);
            if (w != null && w.phase() != WarState.Phase.ENDED) {
                hasPendingWar = true;
                pendingWarId = w.warId();
                pendingPhase = w.phase().name();
                pendingPrepMinutes = w.preparationMinutes();

                UUID opp = civ.id().equals(w.attackerCivId())
                        ? w.defenderCivId()
                        : w.attackerCivId();

                pendingOpponentCivId = opp == null ? new UUID(0L, 0L) : opp;
                Civilization oppCiv = opp == null ? null : data.getCiv(opp);
                pendingOpponentName = (oppCiv != null && oppCiv.name() != null)
                        ? oppCiv.name()
                        : String.valueOf(opp);

                pendingStartsAtMs = switch (w.phase()) {
                    case PREPARING -> w.preparationEndsAtMs();
                    case PROPOSED -> Math.min(
                            w.preparationEndsAtMs() > 0 ? w.preparationEndsAtMs() : Long.MAX_VALUE,
                            w.leaderOnlineDeadlineMs() > 0 ? w.leaderOnlineDeadlineMs() : Long.MAX_VALUE
                    );
                    default -> 0L;
                };
            }
        }

        Network.CH.send(
                new S2C_OpenManageCivScreenPacket(
                        pos,
                        civ.id(),
                        civ.name() == null ? "" : civ.name(),
                        civ.classType() == null ? CivClassType.AGRICULTURAL : civ.classType(),
                        civ.civLevel(),
                        civ.civXp(),
                        civ.members().size(),
                        isLeader,
                        members,
                        pendingInvites,
                        civ.claimCredits(),
                        claimedChunks,
                        maxChunks,
                        hasPendingWar,
                        pendingWarId,
                        pendingPhase,
                        pendingOpponentCivId,
                        pendingOpponentName,
                        pendingStartsAtMs,
                        pendingPrepMinutes
                ),
                PacketDistributor.PLAYER.with(sPlayer)
        );

        return InteractionResult.SUCCESS;
    }
}
