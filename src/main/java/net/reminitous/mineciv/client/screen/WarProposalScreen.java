package net.reminitous.mineciv.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.C2S_AcceptWarPacket;
import net.reminitous.mineciv.net.pkt.C2S_DeclineWarPacket;
import net.reminitous.mineciv.net.pkt.S2C_OpenWarProposalScreenPacket;

public final class WarProposalScreen extends Screen {

    private final S2C_OpenWarProposalScreenPacket data;

    public WarProposalScreen(S2C_OpenWarProposalScreenPacket data) {
        super(Component.literal("War Proposal"));
        this.data = data;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        Button accept = Button.builder(Component.literal("Accept"), b -> onAccept())
                .bounds(cx - 100, cy + 20, 95, 20)
                .build();
        this.addRenderableWidget(accept);

        Button decline = Button.builder(Component.literal("Decline"), b -> onDecline())
                .bounds(cx + 5, cy + 20, 95, 20)
                .build();
        this.addRenderableWidget(decline);

        Button close = Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx - 100, cy + 44, 200, 20)
                .build();
        this.addRenderableWidget(close);
    }

    private void onAccept() {
        Network.CH.send(new C2S_AcceptWarPacket(data.warId), PacketDistributor.SERVER.noArg());
        this.onClose();
    }

    private void onDecline() {
        Network.CH.send(new C2S_DeclineWarPacket(data.warId), PacketDistributor.SERVER.noArg());
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
        int y = (this.height / 2) - 50;

        gfx.drawCenteredString(this.font, this.title, cx, y, 0xFFFFFF);
        y += 16;

        gfx.drawCenteredString(this.font,
                "From: " + (data.attackerCivName == null || data.attackerCivName.isBlank() ? data.attackerCivId.toString() : data.attackerCivName),
                cx, y, 0xAAAAAA
        );
        y += 12;

        gfx.drawCenteredString(this.font,
                "Prep time: " + data.prepMinutes + " minutes",
                cx, y, 0xAAAAAA
        );
        y += 14;

        gfx.drawCenteredString(this.font,
                "Declining still starts war in 24 hours.",
                cx, y, 0xFFAA00
        );

        super.render(gfx, mouseX, mouseY, partialTicks);
    }
}
