package net.reminitous.mineciv.monument;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
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

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class MonumentBlock extends Block implements EntityBlock {

    public MonumentBlock(Properties props) {
        super(props);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MonumentBlockEntity(pos, state);
    }

    // Empty-hand interaction
    @Override
    public InteractionResult useWithoutItem(BlockState state,
                                            Level level,
                                            BlockPos pos,
                                            Player player,
                                            BlockHitResult hit) {
        return handleRightClick(level, pos, player);
    }

    // Item-in-hand interaction (1.21.x): return ItemInteractionResult
    @Override
    public ItemInteractionResult useItemOn(ItemStack stack,
                                           BlockState state,
                                           Level level,
                                           BlockPos pos,
                                           Player player,
                                           InteractionHand hand,
                                           BlockHitResult hit) {

        handleRightClick(level, pos, player);

        // Always consume so items aren't placed/used
        return ItemInteractionResult.CONSUME;
    }

    private InteractionResult handleRightClick(Level level, BlockPos pos, Player player) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResult.PASS;
        }

        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.PASS;
        }

        BlockEntity be = serverLevel.getBlockEntity(pos);
        if (!(be instanceof MonumentBlockEntity monumentBE)) {
            return InteractionResult.PASS;
        }

        // Bound monument => Manage UI
        if (monumentBE.isBound()) {
            UUID civId = monumentBE.getCivId();
            if (civId == null) return InteractionResult.CONSUME;

            CivSavedData data = CivSavedData.get(serverLevel.getServer());
            Civilization civ = data.getCiv(civId);
            if (civ == null) return InteractionResult.CONSUME;

            boolean isLeader = civ.leader() != null && civ.leader().equals(sp.getUUID());
            List<UUID> members = new ArrayList<>(civ.members());

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
                            members
                    ),
                    PacketDistributor.PLAYER.with(sp)
            );

            return InteractionResult.CONSUME;
        }

        // Unbound monument => Create UI
        Network.CH.send(
                new S2C_OpenCreateCivScreenPacket(pos),
                PacketDistributor.PLAYER.with(sp)
        );

        return InteractionResult.CONSUME;
    }
}
