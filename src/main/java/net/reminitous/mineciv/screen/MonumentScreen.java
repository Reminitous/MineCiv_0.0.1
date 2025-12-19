package net.reminitous.mineciv.screen;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.reminitous.mineciv.network.ModMessages;
import net.reminitous.mineciv.network.packet.MonumentAddPlayerC2SPacket;
import net.reminitous.mineciv.network.packet.MonumentRemovePlayerC2SPacket;

import java.util.*;

public class MonumentScreen extends AbstractContainerScreen<MonumentMenu> {
    private EditBox playerNameInput;
    private Button addButton;
    private Button removeButton;
    private List<UUID> allowedPlayersList = new ArrayList<>();
    private Map<UUID, String> playerNames = new HashMap<>();
    private int scrollOffset = 0;
    private static final int MAX_VISIBLE_PLAYERS = 5;

    public MonumentScreen(MonumentMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
        this.imageHeight = 200;
        this.imageWidth = 176;
    }

    @Override
    protected void init() {
        super.init();

        int centerX = (this.width - this.imageWidth) / 2;
        int centerY = (this.height - this.imageHeight) / 2;

        // Player name input field
        this.playerNameInput = new EditBox(this.font, centerX + 10, centerY + 20, 120, 20, Component.literal("Player Name"));
        this.playerNameInput.setMaxLength(16);
        this.playerNameInput.setHint(Component.literal("Enter player name"));
        this.addWidget(this.playerNameInput);

        // Add player button
        this.addButton = Button.builder(Component.literal("Add"), button -> {
            String playerName = this.playerNameInput.getValue().trim();
            if (!playerName.isEmpty()) {
                ModMessages.sendToServer(new MonumentAddPlayerC2SPacket(
                        menu.getMonumentPos(),
                        playerName
                ));
                this.playerNameInput.setValue("");
            }
        }).bounds(centerX + 135, centerY + 20, 30, 20).build();
        this.addRenderableWidget(this.addButton);

        // Remove player button
        this.removeButton = Button.builder(Component.literal("Remove"), button -> {
            String playerName = this.playerNameInput.getValue().trim();
            if (!playerName.isEmpty()) {
                ModMessages.sendToServer(new MonumentRemovePlayerC2SPacket(
                        menu.getMonumentPos(),
                        playerName
                ));
                this.playerNameInput.setValue("");
            }
        }).bounds(centerX + 10, centerY + 45, 155, 20).build();
        this.addRenderableWidget(this.removeButton);

        updateAllowedPlayers();
    }

    private void updateAllowedPlayers() {
        allowedPlayersList.clear();
        playerNames.clear();

        Set<UUID> allowed = menu.getAllowedPlayers(minecraft.player);
        allowedPlayersList.addAll(allowed);

        // Get player names from the server's player list
        if (minecraft.level != null) {
            for (UUID uuid : allowedPlayersList) {
                GameProfile profile = minecraft.level.getPlayerByUUID(uuid) != null ?
                        minecraft.level.getPlayerByUUID(uuid).getGameProfile() : null;

                if (profile != null) {
                    playerNames.put(uuid, profile.getName());
                } else {
                    playerNames.put(uuid, uuid.toString().substring(0, 8) + "...");
                }
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.playerNameInput.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        int centerX = (this.width - this.imageWidth) / 2;
        int centerY = (this.height - this.imageHeight) / 2;

        // Draw background
        guiGraphics.fill(centerX, centerY, centerX + this.imageWidth, centerY + this.imageHeight, 0xFF8B8B8B);
        guiGraphics.fill(centerX + 2, centerY + 2, centerX + this.imageWidth - 2, centerY + this.imageHeight - 2, 0xFF404040);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFFFFFF, false);

        // Draw allowed players list
        guiGraphics.drawString(this.font, "Allowed Players:", 10, 75, 0xFFFFFF, false);

        updateAllowedPlayers();

        int y = 90;
        int count = 0;
        for (int i = scrollOffset; i < allowedPlayersList.size() && count < MAX_VISIBLE_PLAYERS; i++) {
            UUID uuid = allowedPlayersList.get(i);
            String name = playerNames.getOrDefault(uuid, "Unknown");
            guiGraphics.drawString(this.font, "- " + name, 15, y, 0xFFFFFF, false);
            y += 12;
            count++;
        }

        if (allowedPlayersList.isEmpty()) {
            guiGraphics.drawString(this.font, "No players added", 15, 90, 0x888888, false);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (allowedPlayersList.size() > MAX_VISIBLE_PLAYERS) {
            scrollOffset = Math.max(0, Math.min(scrollOffset - (int) scrollY,
                    allowedPlayersList.size() - MAX_VISIBLE_PLAYERS));
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}