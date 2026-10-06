package dev.carbon.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.Font;
import dev.carbon.client.ui.render.CarbonShapes;

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

    /** Draw the required red SDF-failure diagnostic using the stable vanilla font path. */
    protected final void drawCarbonRenderFailure(GuiGraphicsExtractor graphics) {
        String label = "Carbon render failed: " + CarbonShapes.failureReason();
        Font font = Minecraft.getInstance().font;
        float textScale = 1.35f;
        float width = font.width(Component.literal(label)) * textScale;
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(UiScale.snap((UiScale.designWidth() - width) * 0.5f),
                    UiScale.snap(UiScale.designHeight() * 0.5f));
            graphics.pose().scale(textScale, textScale);
            graphics.text(font, Component.literal(label), 0, 0, CarbonTheme.RED, false);
        } finally {
            graphics.pose().popMatrix();
        }
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
