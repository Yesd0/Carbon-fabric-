package dev.carbon.client.ui.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Uses Minecraft's native GUI blur as Carbon's retained-rendering backdrop. */
public final class CarbonBlur {
    public static final String DEBUG_PATH =
            "Blur path: Minecraft native GUI blur (whole backdrop; per-panel framebuffer sampling is not exposed here)";
    private static final String BLUR_DISABLED =
            "Minecraft menu blur is disabled; set Menu Background Blurriness above zero";
    private static final String BLUR_REQUEST_FAILED = "Minecraft native GUI blur request failed";
    private static boolean activeThisFrame;

    private CarbonBlur() {
    }

    /**
     * Marks the next retained GUI stratum for Minecraft's built-in blur pass. The native path is
     * used because 26.2 extracts GUI states before the framebuffer can be sampled by a panel.
     */
    public static boolean requestMenuBlur(GuiGraphicsExtractor graphics, float configuredStrength) {
        activeThisFrame = false;
        if (!Float.isFinite(configuredStrength) || configuredStrength < 1.0f) {
            CarbonGlass.reportFailure(BLUR_DISABLED, null);
            return false;
        }
        try {
            graphics.blurBeforeThisStratum();
            activeThisFrame = true;
            CarbonGlass.clearFailure(BLUR_DISABLED);
            CarbonGlass.clearFailure(BLUR_REQUEST_FAILED);
            return true;
        } catch (Throwable failure) {
            CarbonGlass.reportFailure(BLUR_REQUEST_FAILED, failure);
            return false;
        }
    }

    public static boolean activeThisFrame() {
        return activeThisFrame;
    }
}
