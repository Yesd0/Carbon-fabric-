package dev.carbon.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared non-pausing Carbon screen behavior; each screen owns its matte world overlay. */
public abstract class CarbonScreen extends Screen {
    private final Screen parent;

    protected CarbonScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Preserve the live frame. Carbon surfaces and the 45% matte dimmer are composed by the screen.
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isInGameUi() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
