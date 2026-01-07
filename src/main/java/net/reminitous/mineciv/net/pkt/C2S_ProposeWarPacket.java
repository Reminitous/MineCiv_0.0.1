package net.reminitous.mineciv.net.pkt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.network.CustomPayloadEvent;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.war.WarSavedData;
import net.reminitous.mineciv.war.WarState;

import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.S2C_OpenWarProposalScreenPacket;
import net.minecraftforge.network.PacketDistributor;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class C2S_ProposeWarPacket {

    private static final Set<Integer> ALLOWED_PREP_MINUTES =
            new HashSet<>(Arrays.asList(15, 30, 45, 60));

    private final UUID targetCivId;
    private final int preparationMinutes;

    public C2S_ProposeWarPacket(UUID targetCivId, int preparationMinutes) {
        this.targetCivId = targetCivId;
        this.preparationMinutes = preparationMinutes;
    }

    public static void encode(C2S_ProposeWarPacket msg, FriendlyByteBuf buf) {
        buf.writeUUID(msg.targetCivId);
        buf.writeVarInt(msg.preparationMinutes);
    }

    public static C2S_ProposeWarPacket decode(FriendlyByteBuf buf) {
        UUID target = buf.readUUID();
        int prep = buf.readVarInt();
        return new C2S_ProposeWarPacket(target, prep);
    }

    public static void handle(C2S_ProposeWarPacket msg, CustomPayloadEvent.Context ctx) {
        Object s = ctx.getSender();
        if (!(s instanceof ServerPlayer player)) {
            ctx.setPacketHandled(true);
            return;
        }

        ctx.enqueueWork(() -> {
            if (!(player.level() instanceof ServerLevel level)) return;

            // Validate prep minutes
            if (!ALLOWED_PREP_MINUTES.contains(msg.preparationMinutes)) {
                player.sendSystemMessage(Component.literal("Invalid preparation time. Use 15/30/45/60."));
                return;
            }

            // Find attacker civ
            var attackerOpt = CivilizationManager.findPlayerCiv(level, player.getUUID());
            if (attackerOpt.isEmpty()) {
                player.sendSystemMessage(Component.literal("You are not in a civilization."));
                return;
            }
            Civilization attacker = attackerOpt.get();

            // Must be leader to propose war
            if (attacker.leader() == null || !attacker.leader().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Only civilization leaders may propose war."));
                return;
            }

            // Target civ must exist and not be self
            UUID attackerCivId = attacker.id();
            UUID defenderCivId = msg.targetCivId;

            if (defenderCivId == null) {
                player.sendSystemMessage(Component.literal("Invalid target civilization."));
                return;
            }
            if (defenderCivId.equals(attackerCivId)) {
                player.sendSystemMessage(Component.literal("You cannot declare war on yourself."));
                return;
            }

            CivSavedData civData = CivSavedData.get(level.getServer());
            Civilization defender = civData.getCiv(defenderCivId);
            if (defender == null) {
                player.sendSystemMessage(Component.literal("Target civilization not found."));
                return;
            }

            // Cannot propose war to allies (your rule)
            if (CivilizationManager.areAllies(level, attackerCivId, defenderCivId)) {
                player.sendSystemMessage(Component.literal("You cannot propose war to an ally."));
                return;
            }

            // Neither side can already be in a war
            WarSavedData warData = WarSavedData.get(level.getServer());
            if (warData.getActiveWarId(attackerCivId) != null) {
                player.sendSystemMessage(Component.literal("Your civilization is already involved in a war."));
                return;
            }
            if (warData.getActiveWarId(defenderCivId) != null) {
                player.sendSystemMessage(Component.literal("That civilization is already involved in a war."));
                return;
            }

            // Create war state
            long now = System.currentTimeMillis();
            WarState war = new WarState(UUID.randomUUID());
            war.setAttackerCivId(attackerCivId);
            war.setDefenderCivId(defenderCivId);
            war.setPhase(WarState.Phase.PROPOSED);
            war.setProposedAtMs(now);
            war.setPreparationMinutes(msg.preparationMinutes);
            war.setDefenderAccepted(false);

            // Persist
            warData.putWar(war);
            warData.setActiveWar(attackerCivId, war.warId());
            warData.setActiveWar(defenderCivId, war.warId());

            // Notify attacker
            player.sendSystemMessage(Component.literal(
                    "War proposed to " + (defender.name() == null ? defenderCivId.toString() : defender.name())
                            + " (prep " + msg.preparationMinutes + "m)."
            ));

            // Notify defender members in chat
            notifyCivMembers(level, civData, defender, Component.literal(
                    "⚔ War proposal received from " + (attacker.name() == null ? attackerCivId.toString() : attacker.name())
                            + ". Leader must accept/decline at the monument."
            ));

            // Open accept/decline popup for defender leader if online
            if (defender.leader() != null) {
                ServerPlayer defLeader = level.getServer().getPlayerList().getPlayer(defender.leader());
                if (defLeader != null) {
                    String attackerName = attacker.name() == null ? attackerCivId.toString() : attacker.name();

                    Network.CH.send(
                            new S2C_OpenWarProposalScreenPacket(
                                    war.warId(),
                                    attackerCivId,
                                    attackerName,
                                    war.preparationMinutes(),
                                    war.proposedAtMs()
                            ),
                            PacketDistributor.PLAYER.with(defLeader)
                    );
                }
            }

            // (Next step) Open accept/decline UI for defender leader if online
            // We'll do that in the "Defender accept/decline UI" step.
        });

        ctx.setPacketHandled(true);
    }

    private static void notifyCivMembers(ServerLevel level, CivSavedData civData, Civilization civ, Component msg) {
        for (UUID memberId : civ.members()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) {
                p.sendSystemMessage(msg);
            }
        }
    }
}
