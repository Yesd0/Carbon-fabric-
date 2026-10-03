package dev.carbon.client.ui;

import dev.carbon.client.ui.render.CarbonBlur;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/** Shared non-pausing screen behavior, native Minecraft backdrop blur, and layout-debug toggle. */
public abstract class CarbonScreen extends Screen {
    private static boolean layoutDebugEnabled;

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
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_F7) {
            layoutDebugEnabled = !layoutDebugEnabled;
            return true;
        }
        return super.keyPressed(event);
    }

    public static boolean isLayoutDebugEnabled() {
        return layoutDebugEnabled;
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
