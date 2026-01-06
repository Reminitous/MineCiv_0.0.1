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
import net.reminitous.mineciv.net.pkt.C2S_InvitePlayerPacket;
import net.reminitous.mineciv.net.pkt.C2S_KickMemberPacket;
import net.reminitous.mineciv.net.pkt.C2S_RenameCivPacket;
import net.reminitous.mineciv.net.pkt.S2C_OpenManageCivScreenPacket;

import java.util.UUID;

public final class ManageCivScreen extends Screen {

    private final S2C_OpenManageCivScreenPacket data;

    private EditBox nameBox;
    private EditBox inviteBox;

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

        // Member list (v0: up to 10 shown) + Kick buttons
        int listY = cy - 5;
        int shown = 0;

        UUID self = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getUUID() : null;

        for (UUID memberId : data.members) {
            if (shown >= 10) break;

            final UUID target = memberId;

            Button kick = Button.builder(Component.literal("Kick"), btn -> onKick(target))
                    .bounds(cx + 45, listY, 55, 18)
                    .build();

            boolean canKick = data.isLeader
                    && self != null
                    && !self.equals(target)
                    && data.civId != null; // sanity

            kick.active = canKick;

            this.addRenderableWidget(kick);

            listY += 20;
            shown++;
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

        // Close button
        Button close = Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(cx - 100, cy + 120, 200, 20)
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

        Network.CH.send(
                new C2S_RenameCivPacket(data.monumentPos, data.civId, newName),
                PacketDistributor.SERVER.noArg()
        );
    }

    private void onKick(UUID targetPlayerId) {
        if (!data.isLeader) return;

        Network.CH.send(
                new C2S_KickMemberPacket(data.monumentPos, data.civId, targetPlayerId),
                PacketDistributor.SERVER.noArg()
        );
    }

    private void onInvite() {
        if (!data.isLeader) return;

        String target = inviteBox.getValue().trim();
        if (target.isEmpty()) return;

        Network.CH.send(
                new C2S_InvitePlayerPacket(data.monumentPos, data.civId, target),
                PacketDistributor.SERVER.noArg()
        );

        // Optional: clear box after sending
        inviteBox.setValue("");
    }

    private String resolveName(UUID id) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(id);
            if (info != null) {
                return info.getProfile().getName();
            }
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
        gfx.drawCenteredString(this.font, "Members: " + data.memberCount + (data.isLeader ? " (Leader)" : ""), cx, y, 0xAAAAAA);

        // Render member labels aligned with kick buttons
        int listY = (this.height / 2) - 5;
        int shown = 0;
        for (UUID memberId : data.members) {
            if (shown >= 10) break;
            String name = resolveName(memberId);
            gfx.drawString(this.font, name, cx - 100, listY + 5, 0xFFFFFF);
            listY += 20;
            shown++;
        }

        // Invite label
        gfx.drawString(this.font, "Invite player (online):", cx - 100, (this.height / 2) + 78, 0xAAAAAA);

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
