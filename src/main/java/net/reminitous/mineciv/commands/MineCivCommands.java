package net.reminitous.mineciv.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.territory.TerritoryManager;

import java.util.Optional;
import java.util.UUID;

import static net.minecraft.commands.Commands.literal;

public final class MineCivCommands {

    private MineCivCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                literal("mineciv")
                        .then(literal("whoami")
                                .executes(ctx -> whoAmI(ctx.getSource()))
                        )
                        .then(literal("claim")
                                .executes(ctx -> claimChunk(ctx.getSource()))
                        )

                        .then(literal("ally")
                                .then(literal("list")
                                        .executes(ctx -> allyList(ctx.getSource()))
                                )

                                .then(literal("request")
                                        .then(Commands.argument("civId", UuidArgument.uuid())
                                                .executes(ctx -> allyRequest(
                                                        ctx.getSource(),
                                                        UuidArgument.getUuid(ctx, "civId")
                                                ))
                                        )
                                )
                                .then(literal("accept")
                                        .then(Commands.argument("civId", UuidArgument.uuid())
                                                .executes(ctx -> allyAccept(
                                                        ctx.getSource(),
                                                        UuidArgument.getUuid(ctx, "civId")
                                                ))
                                        )
                                )
                                .then(literal("decline")
                                        .then(Commands.argument("civId", UuidArgument.uuid())
                                                .executes(ctx -> allyDecline(
                                                        ctx.getSource(),
                                                        UuidArgument.getUuid(ctx, "civId")
                                                ))
                                        )
                                )
                                .then(literal("remove")
                                        .then(Commands.argument("civId", UuidArgument.uuid())
                                                .executes(ctx -> allyRemove(
                                                        ctx.getSource(),
                                                        UuidArgument.getUuid(ctx, "civId")
                                                ))
                                        )
                                )
                        )
        );
    }

    /* ---------------- Existing commands ---------------- */

    private static int whoAmI(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();

            CivSavedData data = CivSavedData.get(player.getServer());
            UUID civId = data.getPlayersCiv(player.getUUID());

            if (civId == null) {
                source.sendFailure(Component.literal("No civ mapping found for you."));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Your civId: " + civId), false);
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Command failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int claimChunk(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();

            // Overworld authority
            ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
            if (overworld == null) {
                source.sendFailure(Component.literal("Overworld is null."));
                return 0;
            }

            CivSavedData data = CivSavedData.get(player.getServer());
            UUID civId = data.getPlayersCiv(player.getUUID());

            if (civId == null) {
                source.sendFailure(Component.literal("You are not in a civilization."));
                return 0;
            }

            ChunkPos chunkPos = new ChunkPos(player.blockPosition());

            UUID owner = TerritoryManager.getOwnerCivId(overworld, chunkPos);
            source.sendSuccess(() -> Component.literal(
                    "DEBUG: chunk=" + chunkPos.x + "," + chunkPos.z + " owner=" + owner
            ), false);

            boolean success = TerritoryManager.claimChunk(overworld, civId, chunkPos);

            if (success) {
                source.sendSuccess(
                        () -> Component.literal("Chunk claimed at " + chunkPos.x + ", " + chunkPos.z),
                        false
                );
            } else {
                source.sendFailure(Component.literal("Failed to claim chunk (rules violated or already claimed)."));
            }
            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Command failed: " + e.getMessage()));
            return 0;
        }
    }

    /* ---------------- Ally helpers ---------------- */

    private static Optional<Civilization> playersCiv(ServerPlayer player) {
        return CivilizationManager.findPlayerCiv(player.serverLevel(), player.getUUID());
    }

    private static boolean requireLeader(ServerPlayer player, Civilization civ, CommandSourceStack source) {
        if (civ == null) {
            source.sendFailure(Component.literal("You are not in a civilization."));
            return false;
        }
        if (civ.leader() == null || !civ.leader().equals(player.getUUID())) {
            source.sendFailure(Component.literal("Only the civilization leader can do that."));
            return false;
        }
        return true;
    }

    /* ---------------- Ally commands ---------------- */

    private static int allyList(CommandSourceStack source) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            Civilization civ = playersCiv(player).orElse(null);

            if (civ == null) {
                source.sendFailure(Component.literal("You are not in a civilization."));
                return 0;
            }

            source.sendSuccess(() -> Component.literal("Allies (" + civ.allies().size() + "):"), false);
            for (UUID allyId : civ.allies()) {
                source.sendSuccess(() -> Component.literal(" - " + allyId), false);
            }

            source.sendSuccess(() -> Component.literal("Incoming Ally Requests (" + civ.incomingAllyRequests().size() + "):"), false);
            for (UUID reqId : civ.incomingAllyRequests()) {
                source.sendSuccess(() -> Component.literal(" - " + reqId), false);
            }

            return 1;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Command failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int allyRequest(CommandSourceStack source, UUID targetCivId) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            Civilization civ = playersCiv(player).orElse(null);
            if (!requireLeader(player, civ, source)) return 0;

            boolean ok = CivilizationManager.requestAlliance(player.serverLevel(), civ.id(), targetCivId);
            if (ok) source.sendSuccess(() -> Component.literal("Alliance request sent to " + targetCivId), false);
            else source.sendFailure(Component.literal("Failed to send alliance request."));
            return ok ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Command failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int allyAccept(CommandSourceStack source, UUID fromCivId) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            Civilization civ = playersCiv(player).orElse(null);
            if (!requireLeader(player, civ, source)) return 0;

            boolean ok = CivilizationManager.acceptAlliance(player.serverLevel(), civ.id(), fromCivId);
            if (ok) source.sendSuccess(() -> Component.literal("Alliance formed with " + fromCivId), false);
            else source.sendFailure(Component.literal("No pending request from that civilization."));
            return ok ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Command failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int allyDecline(CommandSourceStack source, UUID fromCivId) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            Civilization civ = playersCiv(player).orElse(null);
            if (!requireLeader(player, civ, source)) return 0;

            boolean ok = CivilizationManager.declineAlliance(player.serverLevel(), civ.id(), fromCivId);
            if (ok) source.sendSuccess(() -> Component.literal("Alliance request declined: " + fromCivId), false);
            else source.sendFailure(Component.literal("No pending request from that civilization."));
            return ok ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Command failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int allyRemove(CommandSourceStack source, UUID otherCivId) {
        try {
            ServerPlayer player = source.getPlayerOrException();
            Civilization civ = playersCiv(player).orElse(null);
            if (!requireLeader(player, civ, source)) return 0;

            boolean ok = CivilizationManager.removeAlliance(player.serverLevel(), civ.id(), otherCivId);
            if (ok) source.sendSuccess(() -> Component.literal("Alliance removed with " + otherCivId), false);
            else source.sendFailure(Component.literal("Failed to remove alliance."));
            return ok ? 1 : 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("Command failed: " + e.getMessage()));
            return 0;
        }
    }
}
