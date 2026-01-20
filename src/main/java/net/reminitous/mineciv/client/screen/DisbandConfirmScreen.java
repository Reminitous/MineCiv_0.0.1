package net.reminitous.mineciv.client.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.minecraftforge.network.PacketDistributor;

import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.C2S_DisbandCivPacket;

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

        Button disband = Button.builder(Component.literal("DISBAND"), btn -> onDisband())
                .bounds(cx - 100, cy + 25, 200, 20)
                .build();
        this.addRenderableWidget(disband);

        Button cancel = Button.builder(Component.literal("Cancel"), btn -> onClose())
                .bounds(cx - 100, cy + 50, 200, 20)
                .build();
        this.addRenderableWidget(cancel);
    }

    private void onDisband() {
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
        int y = (this.height / 2) - 45;

        gfx.drawCenteredString(this.font, this.title, cx, y, 0xFFFFFF);
        y += 14;

        gfx.drawCenteredString(this.font, "Are you sure you want to disband:", cx, y, 0xAAAAAA);
        y += 12;

        gfx.drawCenteredString(this.font, civName.isEmpty() ? "(Unnamed Civ)" : civName, cx, y, 0xFFFFFF);
        y += 14;

        gfx.drawCenteredString(this.font, "This will destroy the monument,", cx, y, 0xFF5555);
        y += 12;
        gfx.drawCenteredString(this.font, "unclaim territory, and release NPCs.", cx, y, 0xFF5555);

        super.render(gfx, mouseX, mouseY, partialTicks);
    }
}
