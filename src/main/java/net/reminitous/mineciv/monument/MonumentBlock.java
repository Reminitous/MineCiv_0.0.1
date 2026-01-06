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

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.S2C_OpenCreateCivScreenPacket;
import net.reminitous.mineciv.net.pkt.S2C_OpenManageCivScreenPacket;
import net.reminitous.mineciv.territory.TerritoryManager;

import java.util.ArrayList;
import java.util.List;
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

    /** Opens UI when right-clicking with EMPTY hand (no item use). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        return handleRightClick(level, pos, player);
    }

    /** Opens UI even while holding items (important for 1.21.x). */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {

        InteractionResult res = handleRightClick(level, pos, player);

        // If we handled it, consume so the held item doesn't activate.
        if (res.consumesAction()) {
            return ItemInteractionResult.SUCCESS;
        }

        // Otherwise let vanilla proceed
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /* ---------------- Core ---------------- */

    private static InteractionResult handleRightClick(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            // Client: let server decide; return success to avoid item use
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel sLevel)) return InteractionResult.PASS;
        if (!(player instanceof ServerPlayer sPlayer)) return InteractionResult.PASS;

        BlockEntity be = sLevel.getBlockEntity(pos);
        if (!(be instanceof MonumentBlockEntity monumentBE)) return InteractionResult.PASS;

        // ---------------- Self-heal (Monument Rot cleanup) ----------------
        if (monumentBE.isBound()) {
            UUID civId = monumentBE.getCivId();
            CivSavedData data = CivSavedData.get(sLevel.getServer());

            // If civ missing, monument crumbles
            if (civId == null || data.getCiv(civId) == null) {
                sLevel.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                sPlayer.sendSystemMessage(Component.literal("This monument has crumbled due to inactivity."));
                return InteractionResult.SUCCESS;
            }
        }

        // ---------------- Open correct UI ----------------
        if (!monumentBE.isBound()) {
            // Open "Create Civ" screen
            Network.CH.send(
                    new S2C_OpenCreateCivScreenPacket(pos),
                    PacketDistributor.PLAYER.with(sPlayer)
            );
            return InteractionResult.SUCCESS;
        }

        // Bound -> open "Manage Civ" screen with current snapshot
        UUID civId = monumentBE.getCivId();
        if (civId == null) return InteractionResult.SUCCESS;

        CivSavedData data = CivSavedData.get(sLevel.getServer());
        Civilization civ = data.getCiv(civId);
        if (civ == null) return InteractionResult.SUCCESS;

        boolean isLeader = civ.leader() != null && civ.leader().equals(sPlayer.getUUID());

        List<UUID> members = new ArrayList<>(civ.members());

        // If you store pending invites elsewhere, replace this list accordingly.
        List<UUID> pendingInvites = new ArrayList<>();

        int claimedChunks = civ.claimedChunks().size();
        int maxChunks = TerritoryManager.MAX_CHUNKS;

        S2C_OpenManageCivScreenPacket pkt = new S2C_OpenManageCivScreenPacket(
                pos,
                civ.id(),
                civ.name(),
                civ.classType(),
                civ.civLevel(),
                civ.civXp(),
                civ.members().size(),
                isLeader,
                members,
                pendingInvites,
                civ.claimCredits(),
                claimedChunks,
                maxChunks
        );

        Network.CH.send(pkt, PacketDistributor.PLAYER.with(sPlayer));
        return InteractionResult.SUCCESS;
    }
}
