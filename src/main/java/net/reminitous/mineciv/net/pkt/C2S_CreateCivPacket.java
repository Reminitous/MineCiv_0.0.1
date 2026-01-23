package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivClassType;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.monument.MonumentBlockEntity;
import net.reminitous.mineciv.registry.ModBlocks;

public final class C2S_CreateCivPacket {

    private final String name;
    private final CivClassType classType;
    private final BlockPos monumentPos;

    public C2S_CreateCivPacket(String name, CivClassType classType, BlockPos monumentPos) {
        this.name = name;
        this.classType = classType;
        this.monumentPos = monumentPos;
    }

    /* ---------------- Serialization ---------------- */

    public static void encode(C2S_CreateCivPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name, 32);
        buf.writeEnum(msg.classType);
        buf.writeBlockPos(msg.monumentPos);
    }

    public static C2S_CreateCivPacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf(32);
        CivClassType type = buf.readEnum(CivClassType.class);
        BlockPos pos = buf.readBlockPos();
        return new C2S_CreateCivPacket(name, type, pos);
    }

    /* ---------------- Handling ---------------- */

    public static void handle(C2S_CreateCivPacket msg, CustomPayloadEvent.Context ctx) {
        if (!(ctx.getSender() instanceof ServerPlayer player)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) return;

            // Prevent chunk generation / spoofing
            if (!level.hasChunkAt(msg.monumentPos)) return;

            // Validate block
            if (level.getBlockState(msg.monumentPos).getBlock() != ModBlocks.MONUMENT.get()) return;

            // Validate block entity
            var be = level.getBlockEntity(msg.monumentPos);
            if (!(be instanceof MonumentBlockEntity monumentBE)) return;

            // Monument already used
            if (monumentBE.isBound()) return;

            // Player must not already be in a civ
            if (CivilizationManager.findPlayerCiv(level, player.getUUID()).isPresent()) return;

            // Chunk must be valid for civ creation
            ChunkPos chunk = new ChunkPos(msg.monumentPos);
            if (!CivilizationManager.canCreateCiv(level, player.getUUID(), chunk)) return;

            // ---------------- CREATE CIV ----------------
            Civilization civ = CivilizationManager.createCiv(
                    level,
                    player.getUUID(),
                    msg.name,
                    msg.classType,
                    msg.monumentPos
            );

            // ---------------- BIND MONUMENT ----------------
            monumentBE.bindToCiv(civ.id());
            monumentBE.setChanged();

            level.sendBlockUpdated(
                    msg.monumentPos,
                    level.getBlockState(msg.monumentPos),
                    level.getBlockState(msg.monumentPos),
                    3
            );
        });

        ctx.setPacketHandled(true);
    }
}

