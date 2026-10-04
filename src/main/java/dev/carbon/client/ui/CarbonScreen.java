package dev.carbon.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared opaque, non-pausing Carbon screen behavior. */
public abstract class CarbonScreen extends Screen {
    private final Screen parent;

    protected CarbonScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
        // Deliberately opaque: Carbon screens never request or draw a glass/blurred backdrop.
        graphics.fill(0, 0, width, height, CarbonTheme.BACKGROUND);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isInGameUi() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
