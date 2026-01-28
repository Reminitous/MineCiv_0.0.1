package net.reminitous.mineciv.client.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.reminitous.mineciv.civ.CivClass;
import net.reminitous.mineciv.net.Network;
import net.reminitous.mineciv.net.pkt.C2S_CreateCivPacket;

public final class CreateCivScreen extends Screen {

    private final BlockPos monumentPos;

    private EditBox nameBox;
    private CivClass selectedClass = CivClass.AGRICULTURAL;

    public CreateCivScreen(BlockPos monumentPos) {
        super(Component.literal("Create Civilization"));
        this.monumentPos = monumentPos;
    }

    @Override
    protected void init() {
        int cx = this.width / 2;
        int cy = this.height / 2;

        this.nameBox = new EditBox(this.font, cx - 100, cy - 40, 200, 20, Component.literal("Civilization Name"));
        this.nameBox.setMaxLength(32);
        this.addRenderableWidget(this.nameBox);

        Button classButton = Button.builder(Component.literal("Class: " + pretty(selectedClass)), btn -> {
            selectedClass = next(selectedClass);
            btn.setMessage(Component.literal("Class: " + pretty(selectedClass)));
        }).bounds(cx - 100, cy - 10, 200, 20).build();
        this.addRenderableWidget(classButton);

        Button createButton = Button.builder(Component.literal("Create"), btn -> onCreate())
                .bounds(cx - 100, cy + 20, 95, 20)
                .build();
        this.addRenderableWidget(createButton);

        Button cancel = Button.builder(Component.literal("Cancel"), btn -> onClose())
                .bounds(cx + 5, cy + 20, 95, 20)
                .build();
        this.addRenderableWidget(cancel);

        this.setInitialFocus(this.nameBox);
    }

    private void onCreate() {
        String name = nameBox.getValue().trim();
        if (name.isEmpty()) {
            if (Minecraft.getInstance().player != null) {
                Minecraft.getInstance().player.sendSystemMessage(Component.literal("Name cannot be empty."));
            }
            return;
        }

        // ✅ Correct client->server send in modern Forge SimpleChannel
        Network.CH.send(
                new C2S_CreateCivPacket(name, selectedClass, monumentPos),
                net.minecraftforge.network.PacketDistributor.SERVER.noArg()
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
        gfx.drawCenteredString(this.font, this.title, cx, (this.height / 2) - 80, 0xFFFFFF);

        gfx.drawString(this.font, "Name:", cx - 100, (this.height / 2) - 55, 0xFFFFFF);
        gfx.drawString(this.font, "Choose a class:", cx - 100, (this.height / 2) - 25, 0xFFFFFF);

        super.render(gfx, mouseX, mouseY, partialTicks);
    }

    private static CivClass next(CivClass t) {
        CivClass[] vals = CivClass.values();
        return vals[(t.ordinal() + 1) % vals.length];
    }

    private static String pretty(CivClass t) {
        return switch (t) {
            case AGRICULTURAL -> "Agricultural";
            case WARLIKE -> "Warlike";
            case TECHNOLOGY -> "Technology";
            case MYSTIC -> "Mystic";
            case MERCHANT -> "Merchant";
        };
    }
}
