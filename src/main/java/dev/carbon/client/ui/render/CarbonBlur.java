package dev.carbon.client.ui.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Screen-background blur integration using Minecraft's retained GUI pipeline.
 * The game's configured menu-blur strength is honored by the native blur pass.
 */
public final class CarbonBlur {
    private CarbonBlur() {
    }

    /**
     * Marks the current GUI stratum for Minecraft's built-in blur pass.
     * Screen.extractBlurredBackground is invoked once per screen frame, which
     * keeps this request within GuiRenderState's one-blur-per-frame contract.
     */
    public static boolean requestMenuBlur(GuiGraphicsExtractor graphics, float configuredStrength) {
        if (!Float.isFinite(configuredStrength) || configuredStrength < 1.0f) {
            return false;
        }
        graphics.blurBeforeThisStratum();
        return true;
    }
}
