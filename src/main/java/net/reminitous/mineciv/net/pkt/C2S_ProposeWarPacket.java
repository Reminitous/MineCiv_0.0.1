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

            var server = level.getServer();

            CivSavedData civData = CivSavedData.get(server);
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

            WarSavedData warData = WarSavedData.get(server);

            // Neither side can already be in a war (mapped)
            UUID aActive = warData.getActiveWarId(attackerCivId);
            if (aActive != null) {
                player.sendSystemMessage(Component.literal("Your civilization is already involved in a war."));
                return;
            }
            UUID dActive = warData.getActiveWarId(defenderCivId);
            if (dActive != null) {
                player.sendSystemMessage(Component.literal("That civilization is already involved in a war."));
                return;
            }

            // Cooldown enforcement (grace + rematch)
            WarStatusSavedData status = WarStatusSavedData.get(server);
            long now = System.currentTimeMillis();

            if (status.isInGrace(attackerCivId, now)) {
                player.sendSystemMessage(Component.literal("Your civilization is in a grace period and cannot propose war."));
                return;
            }
            if (status.isInGrace(defenderCivId, now)) {
                player.sendSystemMessage(Component.literal("That civilization is in a grace period and cannot receive war proposals."));
                return;
            }
            if (status.isRematchBlocked(attackerCivId, defenderCivId, now)) {
                player.sendSystemMessage(Component.literal("Rematch cooldown active: you cannot war this civ yet."));
                return;
            }

            // Prevent duplicates: if a PROPOSED war already exists between these civs, don't create another
            for (WarState w : warData.wars().values()) {
                if (w == null) continue;
                if (w.phase() == WarState.Phase.ENDED) continue;
                UUID a = w.attackerCivId();
                UUID d = w.defenderCivId();
                if (a == null || d == null) continue;

                boolean samePair = (a.equals(attackerCivId) && d.equals(defenderCivId))
                        || (a.equals(defenderCivId) && d.equals(attackerCivId));

                if (samePair) {
                    player.sendSystemMessage(Component.literal("A war proposal already exists between these civilizations."));
                    return;
                }
            }

            // Create war state
            WarState war = new WarState(UUID.randomUUID());
            war.setAttackerCivId(attackerCivId);
            war.setDefenderCivId(defenderCivId);
            war.setPhase(WarState.Phase.PROPOSED);
            war.setProposedAtMs(now);
            war.setPreparationMinutes(msg.preparationMinutes);
            war.setDefenderAccepted(false);

            // 72h hard cap start (if no response/offline forever)
            war.setPreparationEndsAtMs(now + 72L * 60L * 60L * 1000L);

            // Leader-online deadline starts on defender leader login
            war.setLeaderOnlineDeadlineMs(0L);
            war.setLeaderWarnMask(0);

            // Persist
            warData.putWar(war);
            warData.setPendingWar(attackerCivId, war.warId());
            warData.setPendingWar(defenderCivId, war.warId());

            // IMPORTANT: we do NOT setActiveWar mappings until war becomes ACTIVE.
            // (So "already in a war" means ACTIVE war only, not pending proposals.)

            // Notify attacker
            player.sendSystemMessage(Component.literal(
                    "War proposed to " + safeName(defender) + " (prep " + msg.preparationMinutes + "m)."
            ));

            // Notify defender members in chat
            notifyCivMembers(level, defender, Component.literal(
                    "⚔ War proposal received from " + safeName(attacker) + ". Leader must accept/decline at the monument."
            ));

            // Open popup for defender leader if online
            if (defender.leader() != null) {
                ServerPlayer defLeader = server.getPlayerList().getPlayer(defender.leader());
                if (defLeader != null) {
                    Network.CH.send(
                            new S2C_OpenWarProposalScreenPacket(
                                    war.warId(),
                                    attackerCivId,
                                    safeName(attacker),
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

    private static String safeName(Civilization civ) {
        if (civ == null) return "Unknown";
        String n = civ.name();
        return (n == null || n.isBlank()) ? civ.id().toString() : n;
    }
}
