package net.reminitous.mineciv.net.pkt;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.civ.CivSavedData;
import net.reminitous.mineciv.civ.Civilization;
import net.reminitous.mineciv.civ.CivilizationManager;
import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.war.WarSavedData;
import net.reminitous.mineciv.war.WarState;
import net.reminitous.mineciv.war.WarStatusSavedData;
import net.reminitous.mineciv.net.pkt.S2C_OpenWarProposalScreenPacket;

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

            // Must be leader to propose
            if (attacker.leader() == null || !attacker.leader().equals(player.getUUID())) {
                player.sendSystemMessage(Component.literal("Only civilization leaders may propose war."));
                return;
            }

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

            // Cannot propose war to allies
            if (CivilizationManager.areAllies(level, attackerCivId, defenderCivId)) {
                player.sendSystemMessage(Component.literal("You cannot propose war to an ally."));
                return;
            }

            WarSavedData warData = WarSavedData.get(level.getServer());

            // Neither side can already be in an ACTIVE/PENDING war mapping
            if (warData.getActiveWarId(attackerCivId) != null) {
                player.sendSystemMessage(Component.literal("Your civilization is already involved in a war."));
                return;
            }
            if (warData.getActiveWarId(defenderCivId) != null) {
                player.sendSystemMessage(Component.literal("That civilization is already involved in a war."));
                return;
            }

            // --- COOLDOWN ENFORCEMENT ---
            long now = System.currentTimeMillis();
            WarStatusSavedData status = WarStatusSavedData.get(level.getServer());

            // Loser grace: a civ in grace cannot send OR receive new proposals
            if (status.inGrace(attackerCivId, now)) {
                player.sendSystemMessage(Component.literal("Your civilization is in post-war grace and cannot start another war yet."));
                return;
            }
            if (status.inGrace(defenderCivId, now)) {
                player.sendSystemMessage(Component.literal("That civilization is in post-war grace and cannot be targeted yet."));
                return;
            }

            // Rematch cooldown: winner cannot war the same loser for 48h
            // We enforce symmetrically by checking pair cooldown regardless of which side won.
            if (status.inRematchCooldown(attackerCivId, defenderCivId, now)) {
                player.sendSystemMessage(Component.literal("Rematch cooldown is active between these civilizations."));
                return;
            }

            // Create new war
            WarState war = new WarState(UUID.randomUUID());
            war.setAttackerCivId(attackerCivId);
            war.setDefenderCivId(defenderCivId);

            // PROPOSED phase
            war.setPhase(WarState.Phase.PROPOSED);
            war.setProposedAtMs(now);
            war.setPreparationMinutes(msg.preparationMinutes);

            // This field is used as "force start" timestamp in your tick logic.
            // Policy scaffold:
            // - hard cap 72 hours
            war.setPreparationEndsAtMs(now + 72L * 60L * 60L * 1000L);

            warData.putWar(war);

            // NOTE: Do NOT set activeWarId mapping yet — only set when ACTIVE (cleaner).
            // Your tick will set it when the war enters ACTIVE.

            // Notify attacker
            player.sendSystemMessage(Component.literal(
                    "War proposed to " + safeName(defender, defenderCivId) + " (prep " + msg.preparationMinutes + "m)."
            ));

            // Notify defender members
            notifyCivMembers(level, defender, Component.literal(
                    "⚔ War proposal received from " + safeName(attacker, attackerCivId)
                            + ". Leader must accept/decline at the monument."
            ));

            // Open popup for defender leader if online
            if (defender.leader() != null) {
                ServerPlayer defLeader = level.getServer().getPlayerList().getPlayer(defender.leader());
                if (defLeader != null) {
                    Network.CH.send(
                            new S2C_OpenWarProposalScreenPacket(
                                    war.warId(),
                                    attackerCivId,
                                    safeName(attacker, attackerCivId),
                                    war.preparationMinutes(),
                                    war.proposedAtMs()
                            ),
                            PacketDistributor.PLAYER.with(defLeader)
                    );
                }
            }
        });

        ctx.setPacketHandled(true);
    }

    private static void notifyCivMembers(ServerLevel level, Civilization civ, Component msg) {
        for (UUID memberId : civ.members()) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(memberId);
            if (p != null) p.sendSystemMessage(msg);
        }
    }

    private static String safeName(Civilization civ, UUID id) {
        if (civ == null) return String.valueOf(id);
        String n = civ.name();
        return (n == null || n.isBlank()) ? String.valueOf(id) : n;
    }
}
