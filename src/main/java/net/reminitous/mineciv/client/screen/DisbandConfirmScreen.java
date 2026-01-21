package net.reminitous.mineciv.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.C2S_DisbandCivPacket;

import java.util.UUID;
import net.minecraft.core.BlockPos;

public final class DisbandConfirmScreen extends Screen {

    private final BlockPos monumentPos;
    private final UUID civId;
    private final String civName;

    public DisbandConfirmScreen(BlockPos monumentPos, UUID civId, String civName) {
        super(Component.literal("Disband Civilization"));
        this.monumentPos = monumentPos;
        this.civId = civId;
        this.civName = civName == null ? "" : civName;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        Button confirm = Button.builder(Component.literal("CONFIRM DISBAND"), b -> onConfirm())
                .bounds(cx - 100, cy + 10, 200, 20)
                .build();
        this.addRenderableWidget(confirm);

        Button cancel = Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cx - 100, cy + 35, 200, 20)
                .build();
        this.addRenderableWidget(cancel);
    }

    private void onConfirm() {
        Network.CH.send(new C2S_DisbandCivPacket(monumentPos, civId), PacketDistributor.SERVER.noArg());
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
        y += 18;

        gfx.drawCenteredString(this.font,
                "You are about to permanently disband:",
                cx, y, 0xAAAAAA);
        y += 14;

        gfx.drawCenteredString(this.font,
                civName.isEmpty() ? civId.toString() : civName,
                cx, y, 0xFF5555);
        y += 18;

        gfx.drawCenteredString(this.font,
                "This will destroy the monument and release all NPCs.",
                cx, y, 0xAAAAAA);

        super.render(gfx, mouseX, mouseY, partialTicks);
    }
}
