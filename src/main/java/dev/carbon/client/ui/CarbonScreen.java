package dev.carbon.client.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Shared non-pausing Carbon screen behavior; live gameplay remains clear behind the UI surfaces. */
public abstract class CarbonScreen extends Screen {
    private final Screen parent;

    protected CarbonScreen(Component title, Screen parent) {
        super(title);
        this.parent = parent;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        // Intentionally leave the frame untouched: keep the live world visible, without a pause,
        // full-screen fill, blur, or glass effect. CarbonMenuScreen draws its own opaque panels.
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
