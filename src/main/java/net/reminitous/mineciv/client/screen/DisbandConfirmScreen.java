package net.reminitous.mineciv.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.C2S_ConfirmDisbandPacket;

import java.util.UUID;

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

        Button confirm = Button.builder(Component.literal("Confirm Disband"), b -> onConfirm())
                .bounds(cx - 100, cy + 10, 200, 20)
                .build();
        this.addRenderableWidget(confirm);

        Button cancel = Button.builder(Component.literal("Cancel"), b -> onClose())
                .bounds(cx - 100, cy + 35, 200, 20)
                .build();
        this.addRenderableWidget(cancel);
    }

    private void onConfirm() {
        Network.CH.send(new C2S_ConfirmDisbandPacket(monumentPos, civId),
                net.minecraftforge.network.PacketDistributor.SERVER.noArg());
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
        int cy = this.height / 2;

        String nameLine = civName.isEmpty() ? ("Civ: " + civId) : ("Civ: " + civName);

        gfx.drawCenteredString(this.font, this.title, cx, cy - 45, 0xFFFFFF);
        gfx.drawCenteredString(this.font, nameLine, cx, cy - 28, 0xAAAAAA);

        gfx.drawCenteredString(this.font, "This will permanently delete the civ,", cx, cy - 8, 0xFF5555);
        gfx.drawCenteredString(this.font, "destroy the monument, and release NPCs.", cx, cy + 4, 0xFF5555);

        super.render(gfx, mouseX, mouseY, partialTicks);
    }
}
