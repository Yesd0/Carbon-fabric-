package dev.carbon.client.ui;

import dev.carbon.client.ui.render.CarbonBlur;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared non-pausing screen behavior and native Minecraft background blur. */
public abstract class CarbonScreen extends Screen {
    private final Screen parent;

    protected CarbonScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    protected void extractBlurredBackground(GuiGraphicsExtractor graphics) {
        CarbonBlur.requestMenuBlur(graphics, minecraft.options.getMenuBackgroundBlurriness());
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
