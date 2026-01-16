package net.reminitous.mineciv.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.C2S_CancelInvitePacket;
import net.reminitous.mineciv.net.pkt.C2S_ClaimCurrentChunkPacket;
import net.reminitous.mineciv.net.pkt.C2S_DisbandCivPacket;
import net.reminitous.mineciv.net.pkt.C2S_InvitePlayerPacket;
import net.reminitous.mineciv.net.pkt.C2S_KickMemberPacket;
import net.reminitous.mineciv.net.pkt.C2S_LeaveCivPacket;
import net.reminitous.mineciv.net.pkt.C2S_RenameCivPacket;
import net.reminitous.mineciv.net.pkt.C2S_RequestManageCivDataPacket;
import net.reminitous.mineciv.net.pkt.C2S_RequestOpenWarProposalPacket;
import net.reminitous.mineciv.net.pkt.S2C_OpenManageCivScreenPacket;

import java.util.UUID;

public final class ManageCivScreen extends Screen {

    private static final int MAX_MEMBERS_SHOWN = 7;
    private static final int MAX_PENDING_SHOWN = 5;

    private final S2C_OpenManageCivScreenPacket data;

    private EditBox nameBox;
    private EditBox inviteBox;

    private boolean confirmDisband = false;

    public ManageCivScreen(S2C_OpenManageCivScreenPacket data) {
        super(Component.literal("Manage Civilization"));
        this.data = data;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        // Name + rename
        this.nameBox = new EditBox(this.font, cx - 100, cy - 60, 200, 20, Component.literal("Civilization Name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(data.civName == null ? "" : data.civName);
        this.nameBox.setEditable(data.isLeader);
        this.addRenderableWidget(this.nameBox);

        Button rename = Button.builder(Component.literal("Rename"), btn -> onRename())
                .bounds(cx - 100, cy - 35, 200, 20)
                .build();
        rename.active = data.isLeader;
        this.addRenderableWidget(rename);

        // Members list + kick
        int listY = cy - 5;
        int shown = 0;

        UUID self = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;

        for (UUID memberId : data.members) {
            if (shown >= MAX_MEMBERS_SHOWN) break;

            final UUID target = memberId;

            Button kick = Button.builder(Component.literal("Kick"), btn -> onKick(target))
                    .bounds(cx + 45, listY, 55, 18)
                    .build();

            boolean canKick = data.isLeader && self != null && !self.equals(target);
            kick.active = canKick;
            this.addRenderableWidget(kick);

            listY += 20;
            shown++;
        }

        // Pending invites + cancel buttons
        int invY = cy + 35;
        int invShown = 0;

        for (UUID invitedId : data.pendingInvites) {
            if (invShown >= MAX_PENDING_SHOWN) break;

            final UUID target = invitedId;

            Button cancel = Button.builder(Component.literal("Cancel"), btn -> onCancelInvite(target))
                    .bounds(cx + 45, invY, 55, 18)
                    .build();
            cancel.active = data.isLeader;
            this.addRenderableWidget(cancel);

            invY += 20;
            invShown++;
        }

        // Invite box + Invite button (leader only)
        this.inviteBox = new EditBox(this.font, cx - 100, cy + 90, 140, 20, Component.literal("Player name"));
        this.inviteBox.setMaxLength(32);
        this.inviteBox.setEditable(data.isLeader);
        this.inviteBox.setValue("");
        this.addRenderableWidget(this.inviteBox);

        Button inviteBtn = Button.builder(Component.literal("Invite"), btn -> onInvite())
                .bounds(cx + 45, cy + 90, 55, 20)
                .build();
        inviteBtn.active = data.isLeader;
        this.addRenderableWidget(inviteBtn);

        // Claim current chunk (leader only)
        Button claim = Button.builder(Component.literal("Claim Current Chunk"), btn -> onClaimCurrentChunk())
                .bounds(cx - 100, cy + 112, 200, 20)
                .build();
        claim.active = data.isLeader && data.claimCredits > 0;
        this.addRenderableWidget(claim);

        // --- NEW: Pending war status button (leader only; PROPOSED only) ---
        if (data.isLeader && data.hasPendingWar && "PROPOSED".equalsIgnoreCase(data.pendingPhase) && data.pendingWarId != null) {
            Button openWar = Button.builder(Component.literal("Open War Proposal"), btn -> onOpenWarProposal())
                    .bounds(cx - 100, cy + 160, 200, 20)
                    .build();
            this.addRenderableWidget(openWar);
        }

        // Leave / Disband + Close row
        if (data.isLeader) {
            Button disband = Button.builder(Component.literal(confirmDisband ? "Confirm Disband" : "Disband Civ"), btn -> onDisband())
                    .bounds(cx - 100, cy + 136, 95, 20)
                    .build();
            this.addRenderableWidget(disband);
        } else {
            Button leave = Button.builder(Component.literal("Leave Civ"), btn -> onLeave())
                    .bounds(cx - 100, cy + 136, 95, 20)
                    .build();
            this.addRenderableWidget(leave);
        }

        Button close = Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(cx + 5, cy + 136, 95, 20)
                .build();
        this.addRenderableWidget(close);

        this.setInitialFocus(this.nameBox);
    }

    private void onRename() {
        if (!data.isLeader) return;

        String newName = nameBox.getValue().trim();
        if (newName.isEmpty()) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Name cannot be empty."));
            }
            return;
        }

        Network.CH.send(new C2S_RenameCivPacket(data.monumentPos, data.civId, newName), PacketDistributor.SERVER.noArg());
        requestRefresh();
    }

    private void onKick(UUID targetPlayerId) {
        if (!data.isLeader) return;

        Network.CH.send(new C2S_KickMemberPacket(data.monumentPos, data.civId, targetPlayerId), PacketDistributor.SERVER.noArg());
        requestRefresh();
    }

    private void onInvite() {
        if (!data.isLeader) return;

        String target = inviteBox.getValue().trim();
        if (target.isEmpty()) return;

        Network.CH.send(new C2S_InvitePlayerPacket(data.monumentPos, data.civId, target), PacketDistributor.SERVER.noArg());
        inviteBox.setValue("");
        requestRefresh();
    }

    private void onCancelInvite(UUID invitedPlayerId) {
        if (!data.isLeader) return;

        Network.CH.send(new C2S_CancelInvitePacket(data.monumentPos, data.civId, invitedPlayerId), PacketDistributor.SERVER.noArg());
        requestRefresh();
    }

    private void onClaimCurrentChunk() {
        if (!data.isLeader) return;

        Network.CH.send(new C2S_ClaimCurrentChunkPacket(data.monumentPos, data.civId), PacketDistributor.SERVER.noArg());
        requestRefresh();
    }

    private void onOpenWarProposal() {
        if (!data.isLeader) return;
        if (!data.hasPendingWar || data.pendingWarId == null) return;
        if (!"PROPOSED".equalsIgnoreCase(data.pendingPhase)) return;

        Network.CH.send(new C2S_RequestOpenWarProposalPacket(data.pendingWarId), PacketDistributor.SERVER.noArg());
    }

    private void onLeave() {
        Network.CH.send(new C2S_LeaveCivPacket(data.monumentPos, data.civId), PacketDistributor.SERVER.noArg());
        this.onClose();
    }

    private void onDisband() {
        if (!data.isLeader) return;

        if (!confirmDisband) {
            confirmDisband = true;
            this.clearWidgets();
            this.init();
            return;
        }

        Network.CH.send(new C2S_DisbandCivPacket(data.monumentPos, data.civId), PacketDistributor.SERVER.noArg());
        this.onClose();
    }

    private void requestRefresh() {
        Network.CH.send(new C2S_RequestManageCivDataPacket(data.monumentPos, data.civId), PacketDistributor.SERVER.noArg());
    }

    private String resolveName(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) return info.getProfile().getName();
        }
        String s = id.toString();
        return s.substring(0, 8);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gfx, mouseX, mouseY, partialTicks);

        int cx = this.width / 2;
        int y = (this.height / 2) - 95;

        gfx.drawCenteredString(this.font, this.title, cx, y, 0xFFFFFF);

        y += 16;
        gfx.drawCenteredString(this.font, "Civ: " + (data.civName == null ? "" : data.civName), cx, y, 0xFFFFFF);

        y += 12;
        gfx.drawCenteredString(this.font, "Class: " + pretty(data.classType), cx, y, 0xAAAAAA);

        y += 12;
        gfx.drawCenteredString(this.font, "Level: " + data.civLevel + "  XP: " + data.civXp, cx, y, 0xAAAAAA);

        y += 12;
        gfx.drawCenteredString(this.font, "Claim Credits: " + data.claimCredits, cx, y, 0xAAAAAA);

        y += 12;
        gfx.drawCenteredString(this.font, "Members: " + data.memberCount + (data.isLeader ? " (Leader)" : ""), cx, y, 0xAAAAAA);

        // --- NEW: Pending war status line ---
        y += 14;
        if (data.hasPendingWar) {
            String opp = (data.pendingOpponentName == null || data.pendingOpponentName.isBlank())
                    ? String.valueOf(data.pendingOpponentCivId)
                    : data.pendingOpponentName;
            String phase = (data.pendingPhase == null || data.pendingPhase.isBlank()) ? "UNKNOWN" : data.pendingPhase;

            gfx.drawCenteredString(this.font,
                    "War: " + phase + " vs " + opp,
                    cx, y, 0xFFCC55);
        } else {
            gfx.drawCenteredString(this.font, "War: None", cx, y, 0x55FF55);
        }

        // Members
        int listY = (this.height / 2) - 5;
        int shown = 0;
        for (UUID memberId : data.members) {
            if (shown >= MAX_MEMBERS_SHOWN) break;
            gfx.drawString(this.font, resolveName(memberId), cx - 100, listY + 5, 0xFFFFFF);
            listY += 20;
            shown++;
        }

        // Pending invites
        gfx.drawString(this.font, "Pending invites:", cx - 100, (this.height / 2) + 23, 0xAAAAAA);

        int invY = (this.height / 2) + 35;
        int invShown = 0;
        for (UUID invited : data.pendingInvites) {
            if (invShown >= MAX_PENDING_SHOWN) break;
            gfx.drawString(this.font, resolveName(invited), cx - 100, invY + 5, 0xFFFFFF);
            invY += 20;
            invShown++;
        }

        // Invite label
        gfx.drawString(this.font, "Invite player (online):", cx - 100, (this.height / 2) + 78, 0xAAAAAA);

        if (confirmDisband) {
            gfx.drawString(this.font, "Click Confirm Disband to permanently delete this civ.", cx - 100, (this.height / 2) + 160, 0xFF5555);
        }

        super.render(gfx, mouseX, mouseY, partialTicks);
    }

    private static String pretty(net.reminitous.mineciv.civ.CivClassType t) {
        if (t == null) return "Unknown";
        return switch (t) {
            case AGRICULTURAL -> "Agricultural";
            case WARLIKE -> "Warlike";
            case TECHNOLOGY -> "Technology";
            case MYSTIC -> "Mystic";
            case MERCHANT -> "Merchant";
        };
    }
}
