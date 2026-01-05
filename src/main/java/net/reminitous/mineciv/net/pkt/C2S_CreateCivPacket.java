package net.reminitous.mineciv.net.pkt;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivClassType;
import net.reminitous.mineciv.civ.CivilizationManager;

public final class C2S_CreateCivPacket {

    private final String name;
    private final CivClassType classType;
    private final BlockPos monumentPos;

    public C2S_CreateCivPacket(String name, CivClassType classType, BlockPos monumentPos) {
        this.name = name;
        this.classType = classType;
        this.monumentPos = monumentPos;
    }

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

    public static void handle(C2S_CreateCivPacket msg, net.minecraftforge.event.network.CustomPayloadEvent.Context ctx) {
        var sender = ctx.getSender();
        if (!(sender instanceof net.minecraft.server.level.ServerPlayer player)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) return;

            // Prevent chunk generation exploits
            if (!level.hasChunkAt(msg.monumentPos)) return;

            // Validate block + BE
            var state = level.getBlockState(msg.monumentPos);
            if (state.getBlock() != net.reminitous.mineciv.registry.ModBlocks.MONUMENT.get()) return;

            var be = level.getBlockEntity(msg.monumentPos);
            if (!(be instanceof net.reminitous.mineciv.monument.MonumentBlockEntity monumentBE)) return;

            // Monument already bound → reject
            if (monumentBE.isBound()) return;

            // Player must not already be in a civ
            if (CivilizationManager.findPlayerCiv(level, player.getUUID()).isPresent()) return;

            ChunkPos chunk = new ChunkPos(msg.monumentPos);
            if (!CivilizationManager.canCreateCiv(level, player.getUUID(), chunk)) return;

// ---- CREATE CIV ----
            var civ = CivilizationManager.createCiv(
                    level,
                    player.getUUID(),
                    msg.name,
                    msg.classType,
                    msg.monumentPos
            );

// DEBUG mapping
            var data = net.reminitous.mineciv.civ.CivSavedData.get(level.getServer());
            var mapped = data.getPlayersCiv(player.getUUID());
            net.reminitous.mineciv.MineCiv.LOGGER.info("MineCiv DEBUG: after createCiv, player {} mapped to {}", player.getUUID(), mapped);
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("DEBUG: mapping after create = " + mapped));

// ---- BIND MONUMENT ----
            monumentBE.setCivId(civ.id());

        });

        ctx.setPacketHandled(true);
    }
}
