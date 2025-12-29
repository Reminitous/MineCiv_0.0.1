package net.reminitous.mineciv.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.reminitous.mineciv.civ.CivilizationType;

public class MonumentScreen extends AbstractContainerScreen<MonumentMenu> {

    public MonumentScreen(MonumentMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        // No slots, so we can make the screen smaller
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();

        int x = this.leftPos;
        int y = this.topPos;

        // 5 buttons, vertical list
        addRenderableWidget(Button.builder(Component.literal("Warrior"), btn -> sendChoice(0))
                .bounds(x + 20, y + 25, 136, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Engineer"), btn -> sendChoice(1))
                .bounds(x + 20, y + 50, 136, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Farmer"), btn -> sendChoice(2))
                .bounds(x + 20, y + 75, 136, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Mystic"), btn -> sendChoice(3))
                .bounds(x + 20, y + 100, 136, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Merchant"), btn -> sendChoice(4))
                .bounds(x + 20, y + 125, 136, 20).build());
    }

    private void sendChoice(int id) {
        // This calls MonumentMenu#clickMenuButton on the server
        Minecraft mc = this.minecraft;
        if (mc != null && mc.gameMode != null) {
            mc.gameMode.handleInventoryButtonClick(this.menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        // Simple dark background rectangle (no texture needed)
        gfx.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xAA000000);
        gfx.fill(leftPos + 4, topPos + 4, leftPos + imageWidth - 4, topPos + imageHeight - 4, 0xAA202020);
    }

    @Override
    protected void renderLabels(GuiGraphics gfx, int mouseX, int mouseY) {
        gfx.drawString(this.font, "Monument Management", 8, 6, 0xFFFFFF);

        CivilizationType current = this.menu.getCivilizationType();
        gfx.drawString(this.font, "Current: " + current.name(), 8, 16, 0xCFCFCF);
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        renderBackground(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        renderTooltip(gfx, mouseX, mouseY);
    }
}
