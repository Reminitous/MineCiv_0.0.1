package net.reminitous.mineciv.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.C2S_RespondInvitePacket;
import net.reminitous.mineciv.net.pkt.S2C_OpenInvitePopupPacket;

public final class InvitePopupScreen extends Screen {

    private final S2C_OpenInvitePopupPacket data;

    public InvitePopupScreen(S2C_OpenInvitePopupPacket data) {
        super(Component.literal("Civilization Invite"));
        this.data = data;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        Button accept = Button.builder(Component.literal("Accept"), btn -> respond(true))
                .bounds(cx - 100, cy + 10, 95, 20)
                .build();
        this.addRenderableWidget(accept);

        Button decline = Button.builder(Component.literal("Decline"), btn -> respond(false))
                .bounds(cx + 5, cy + 10, 95, 20)
                .build();
        this.addRenderableWidget(decline);
    }

    private void respond(boolean accept) {
        Network.CH.send(
                new C2S_RespondInvitePacket(data.civId, accept),
                PacketDistributor.SERVER.noArg()
        );
        this.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTicks) {
        this.renderBackground(gfx, mouseX, mouseY, partialTicks);

        int cx = this.width / 2;
        int y = (this.height / 2) - 40;

        gfx.drawCenteredString(this.font, this.title, cx, y, 0xFFFFFF);
        y += 16;
        gfx.drawCenteredString(this.font, "You were invited to join:", cx, y, 0xAAAAAA);
        y += 14;
        gfx.drawCenteredString(this.font, data.civName, cx, y, 0xFFFFFF);
        y += 14;
        gfx.drawCenteredString(this.font, "Class: " + pretty(), cx, y, 0xAAAAAA);

        super.render(gfx, mouseX, mouseY, partialTicks);
    }

    private String pretty() {
        return switch (data.classType) {
            case AGRICULTURAL -> "Agricultural";
            case WARLIKE -> "Warlike";
            case TECHNOLOGY -> "Technology";
            case MYSTIC -> "Mystic";
            case MERCHANT -> "Merchant";
        };
    }
}
