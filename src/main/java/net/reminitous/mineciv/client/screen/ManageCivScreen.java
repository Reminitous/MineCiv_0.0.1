package net.reminitous.mineciv.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.civ.CivClass;
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
import net.reminitous.mineciv.net.pkt.C2S_ClaimAdjacentToMonumentPacket;

import java.util.UUID;

public final class ManageCivScreen extends Screen {

    private static final int MAX_MEMBERS_SHOWN = 7;
    private static final int MAX_PENDING_SHOWN = 5;

    private final S2C_OpenManageCivScreenPacket data;

    private EditBox nameBox;
    private EditBox inviteBox;

    private boolean confirmDisband = false;

    // Layout anchors so render() aligns text with buttons
    private int _membersStartY = 0;
    private int _pendingLabelY = 0;
    private int _pendingStartY = 0;
    private int _inviteTopY = 0;
    private int _claimLabelY = 0;

    // For "...and more" messaging in render()
    private int _membersShown = 0;
    private int _pendingShown = 0;

    public ManageCivScreen(S2C_OpenManageCivScreenPacket data) {
        super(Component.literal("Manage Civilization"));
        this.data = data;
    }

    @Override
    protected void init() {
        this.clearWidgets();

        int cx = this.width / 2;
        int cy = this.height / 2;

        int xLeft = cx - 100;
        int xRightBtn = cx + 45;

        int wMain = 200;
        int hMain = 20;

        int rowGap = 4;
        int blockGap = 10;

        int topMargin = 18;
        int bottomMargin = 18;

        // Reserve space for your header (title + civ stats + war line) drawn in render()
        int headerReserve = 110;

        int topY = topMargin + headerReserve;

        // --- Build the bottom panel FIRST so it is always visible ---
        boolean showWarBtn = data.isLeader && data.hasPendingWar
                && "PROPOSED".equalsIgnoreCase(data.pendingPhase)
                && data.pendingWarId != null;

        // Bottom panel height:
        // Claim Current Chunk (20)
        // gap
        // Directions cross (N row + W/E row + S row) approx 58px
        // gap
        // optional war (20 + gap)
        // bottom row (20)
        int directionsHeight = 18 + 2 + 18 + 2 + 18; // 58
        int bottomPanelHeight =
                hMain + rowGap +                 // Claim Current Chunk
                        directionsHeight + blockGap +     // Directions + gap after
                        (showWarBtn ? (hMain + blockGap) : 0) +  // Optional war + gap after
                        hMain;                            // Disband/Close (or Leave/Close)

        int bottomPanelTopY = this.height - bottomMargin - bottomPanelHeight;

        // If the screen is extremely short, ensure we don't go above the top area too much
        bottomPanelTopY = Math.max(bottomPanelTopY, topY + 40);

        // --- TOP FLOW: Name box + Rename, Invite box + Invite ---
        int y = topY;

        // Name box
        this.nameBox = new EditBox(this.font, xLeft, y, wMain, hMain, Component.literal("Civilization Name"));
        this.nameBox.setMaxLength(32);
        this.nameBox.setValue(data.civName == null ? "" : data.civName);
        this.nameBox.setEditable(data.isLeader);
        this.addRenderableWidget(this.nameBox);

        y += hMain + rowGap;

        // Rename button (top button)
        Button rename = Button.builder(Component.literal("Rename"), btn -> onRename())
                .bounds(xLeft, y, wMain, hMain)
                .build();
        rename.active = data.isLeader;
        this.addRenderableWidget(rename);

        y += hMain + blockGap;

        // Invite label anchor for render()
        _inviteTopY = y;

        // Invite box
        this.inviteBox = new EditBox(this.font, xLeft, y, wMain, hMain, Component.literal("Player name"));
        this.inviteBox.setMaxLength(32);
        this.inviteBox.setEditable(data.isLeader);
        this.inviteBox.setValue("");
        this.addRenderableWidget(this.inviteBox);

        y += hMain + rowGap;

        // Invite button
        Button inviteBtn = Button.builder(Component.literal("Invite"), btn -> onInvite())
                .bounds(xLeft, y, wMain, hMain)
                .build();
        inviteBtn.active = data.isLeader;
        this.addRenderableWidget(inviteBtn);

        y += hMain + blockGap;

        // --- MIDDLE AREA: Members + Pending lists (AUTO-SHRINK to fit above bottom panel) ---
        int listAreaTop = y;
        int listAreaBottom = bottomPanelTopY - blockGap;
        int listAreaHeight = Math.max(0, listAreaBottom - listAreaTop);

        int pendingLabelH = 12 + 8; // label line + spacing
        int rowH = 20;

        boolean hasPending = data.pendingInvites != null && !data.pendingInvites.isEmpty();
        int reservedForPendingLabel = hasPending ? pendingLabelH : 0;

        int usableForRows = listAreaHeight - reservedForPendingLabel;
        int totalRowsFit = usableForRows > 0 ? (usableForRows / rowH) : 0;

        int membersTotal = data.members == null ? 0 : data.members.size();
        int pendingTotal = data.pendingInvites == null ? 0 : data.pendingInvites.size();

        // Show members first, then pending with remaining
        int membersShow = Math.min(MAX_MEMBERS_SHOWN, Math.min(membersTotal, totalRowsFit));
        int remainingRows = Math.max(0, totalRowsFit - membersShow);
        int pendingShow = hasPending ? Math.min(MAX_PENDING_SHOWN, Math.min(pendingTotal, remainingRows)) : 0;

        _membersShown = membersShow;
        _pendingShown = pendingShow;

        // Members buttons + name alignment anchors
        _membersStartY = listAreaTop;

        int listY = _membersStartY;
        UUID self = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;

        for (int i = 0; i < membersShow; i++) {
            UUID target = data.members.get(i);

            Button kick = Button.builder(Component.literal("Kick"), btn -> onKick(target))
                    .bounds(xRightBtn, listY, 55, 18)
                    .build();

            boolean canKick = data.isLeader && self != null && !self.equals(target);
            kick.active = canKick;
            this.addRenderableWidget(kick);

            listY += rowH;
        }

        // Pending invites buttons + anchors
        _pendingLabelY = listY + 8;
        _pendingStartY = _pendingLabelY + 12;

        int invY = _pendingStartY;
        for (int i = 0; i < pendingShow; i++) {
            UUID target = data.pendingInvites.get(i);

            Button cancel = Button.builder(Component.literal("Cancel"), btn -> onCancelInvite(target))
                    .bounds(xRightBtn, invY, 55, 18)
                    .build();
            cancel.active = data.isLeader;
            this.addRenderableWidget(cancel);

            invY += rowH;
        }

        // --- BOTTOM PANEL (pinned): Claim + Directions + (optional war) + Disband/Close ---
        int bpY = bottomPanelTopY;

        // Claim current chunk
        Button claim = Button.builder(Component.literal("Claim Current Chunk"), btn -> onClaimCurrentChunk())
                .bounds(xLeft, bpY, wMain, hMain)
                .build();
        claim.active = data.isLeader && data.claimCredits > 0;
        this.addRenderableWidget(claim);

        bpY += hMain + rowGap;

        // Directional cross centered under claim
        int bx = cx - 25;
        int by = bpY;

        Button north = Button.builder(Component.literal("N"), b -> onClaimDir(C2S_ClaimAdjacentToMonumentPacket.Dir.NORTH))
                .bounds(bx, by, 50, 18)
                .build();
        north.active = data.isLeader && data.claimCredits > 0;
        this.addRenderableWidget(north);

        Button west = Button.builder(Component.literal("W"), b -> onClaimDir(C2S_ClaimAdjacentToMonumentPacket.Dir.WEST))
                .bounds(bx - 55, by + 20, 50, 18)
                .build();
        west.active = data.isLeader && data.claimCredits > 0;
        this.addRenderableWidget(west);

        Button east = Button.builder(Component.literal("E"), b -> onClaimDir(C2S_ClaimAdjacentToMonumentPacket.Dir.EAST))
                .bounds(bx + 55, by + 20, 50, 18)
                .build();
        east.active = data.isLeader && data.claimCredits > 0;
        this.addRenderableWidget(east);

        Button south = Button.builder(Component.literal("S"), b -> onClaimDir(C2S_ClaimAdjacentToMonumentPacket.Dir.SOUTH))
                .bounds(bx, by + 40, 50, 18)
                .build();
        south.active = data.isLeader && data.claimCredits > 0;
        this.addRenderableWidget(south);

        // Label anchor for render() near the directions
        _claimLabelY = by - 12;

        bpY = by + 40 + 18 + blockGap;

        // Optional war proposal button
        if (showWarBtn) {
            Button openWar = Button.builder(Component.literal("Open War Proposal"), btn -> onOpenWarProposal())
                    .bounds(xLeft, bpY, wMain, hMain)
                    .build();
            this.addRenderableWidget(openWar);

            bpY += hMain + blockGap;
        }

        // Disband/Leave + Close row (ALWAYS visible)
        if (data.isLeader) {
            Button disband = Button.builder(Component.literal(confirmDisband ? "Confirm Disband" : "Disband Civ"), btn -> onDisband())
                    .bounds(xLeft, bpY, 95, hMain)
                    .build();
            this.addRenderableWidget(disband);
        } else {
            Button leave = Button.builder(Component.literal("Leave Civ"), btn -> onLeave())
                    .bounds(xLeft, bpY, 95, hMain)
                    .build();
            this.addRenderableWidget(leave);
        }

        Button close = Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(xLeft + 105, bpY, 95, hMain)
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

    private void onClaimDir(C2S_ClaimAdjacentToMonumentPacket.Dir dir) {
        if (!data.isLeader) return;

        Network.CH.send(new C2S_ClaimAdjacentToMonumentPacket(data.monumentPos, data.civId, dir), PacketDistributor.SERVER.noArg());
        // Refresh typically happens via S2C_ClaimFeedbackPacket; leaving as-is.
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

    // CALLED BY S2C_ClaimFeedbackPacket when the screen is open
    public void requestRefreshFromClient() {
        requestRefresh();
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

        // Pending war status line
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

        // Invite label (above invite box)
        gfx.drawString(this.font, "Invite player (online):", cx - 100, _inviteTopY - 12, 0xAAAAAA);

        // Members list (aligned to kick buttons)
        int listY = _membersStartY;
        for (int i = 0; i < _membersShown; i++) {
            UUID memberId = data.members.get(i);
            gfx.drawString(this.font, resolveName(memberId), cx - 100, listY + 5, 0xFFFFFF);
            listY += 20;
        }
        if (data.members.size() > _membersShown) {
            gfx.drawString(this.font, "...and " + (data.members.size() - _membersShown) + " more", cx - 100, listY + 3, 0xAAAAAA);
        }

        // Pending invites
        gfx.drawString(this.font, "Pending invites:", cx - 100, _pendingLabelY, 0xAAAAAA);

        int invY = _pendingStartY;
        for (int i = 0; i < _pendingShown; i++) {
            UUID invited = data.pendingInvites.get(i);
            gfx.drawString(this.font, resolveName(invited), cx - 100, invY + 5, 0xFFFFFF);
            invY += 20;
        }
        if (data.pendingInvites.size() > _pendingShown) {
            gfx.drawString(this.font, "...and " + (data.pendingInvites.size() - _pendingShown) + " more", cx - 100, invY + 3, 0xAAAAAA);
        }

        // Claim Adjacent label near N/W/E/S (pinned area)
        gfx.drawString(this.font, "Claim Adjacent:", cx - 100, _claimLabelY, 0xAAAAAA);

        if (confirmDisband) {
            int warnX = cx - 100;
            int warnMaxW = 200;

            // Put warning in a visible area above the bottom buttons
            int warnY = Math.min(this.height - 80, invY + 50);

            var lines = this.font.split(
                    Component.literal("Click Confirm Disband to permanently delete this civ."),
                    warnMaxW
            );

            for (var line : lines) {
                gfx.drawString(this.font, line, warnX, warnY, 0xFF5555);
                warnY += this.font.lineHeight;
            }
        }

        super.render(gfx, mouseX, mouseY, partialTicks);
    }

    private static String pretty(CivClass t) {
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
