package dev.carbon.client.ui;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Converts the 1920x1080 design canvas to Minecraft's current GUI coordinates. */
public final class UiScale {
    public static final float DESIGN_WIDTH = 1920.0f;
    public static final float DESIGN_HEIGHT = 1080.0f;
    public static final float MIN_SCALE = 0.6f;
    public static final float MAX_SCALE = 1.6f;

    private static int framebufferWidth = 1920;
    private static int framebufferHeight = 1080;
    private static int guiScale = 1;
    private static float uiScale = 1.0f;
    private static float rendererScale = 1.0f;
    private static float designWidth = DESIGN_WIDTH;
    private static float designHeight = DESIGN_HEIGHT;
    private static boolean initialized;

    private UiScale() {
    }

    /** Refreshes cached dimensions. Returns true only when the framebuffer or GUI scale changed. */
    public static boolean update(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        int nextGuiScale = Math.max(1, window.getGuiScale());
        // Gui-scaled extents are derived from the framebuffer and are exposed consistently in 26.2.
        int nextFramebufferWidth = Math.max(1, window.getGuiScaledWidth() * nextGuiScale);
        int nextFramebufferHeight = Math.max(1, window.getGuiScaledHeight() * nextGuiScale);
        boolean changed = nextFramebufferWidth != framebufferWidth
                || nextFramebufferHeight != framebufferHeight
                || nextGuiScale != guiScale;

        if (!initialized || changed) {
            framebufferWidth = nextFramebufferWidth;
            framebufferHeight = nextFramebufferHeight;
            guiScale = nextGuiScale;
            float resolutionScale = Math.min(framebufferWidth / DESIGN_WIDTH,
                    framebufferHeight / DESIGN_HEIGHT);
            uiScale = clamp(resolutionScale, MIN_SCALE, MAX_SCALE);
            rendererScale = uiScale / guiScale;
            designWidth = framebufferWidth / uiScale;
            designHeight = framebufferHeight / uiScale;
            initialized = true;
        }
        return changed;
    }

    public static float uiScale() {
        return uiScale;
    }

    public static int guiScale() {
        return guiScale;
    }

    public static int framebufferWidth() {
        return framebufferWidth;
    }

    public static int framebufferHeight() {
        return framebufferHeight;
    }

    public static float rendererScale() {
        return rendererScale;
    }

    public static float designWidth() {
        return designWidth;
    }

    public static float designHeight() {
        return designHeight;
    }

    /** Converts design-pixel coordinates into the current renderer's coordinate space. */
    public static float toRenderer(float designPx) {
        return designPx * rendererScale;
    }

    /** Converts a mouse coordinate from Minecraft GUI space back to design pixels. */
    public static float toDesign(float guiCoordinate) {
        return guiCoordinate / rendererScale;
    }

    /** Snaps a design coordinate or size to a whole framebuffer pixel. */
    public static float snap(float designPx) {
        return Math.round(designPx * uiScale) / uiScale;
    }

    public static int framebufferPixels(float designPx) {
        return Math.round(designPx * uiScale);
    }

    public static void pushRendererScale(GuiGraphicsExtractor graphics) {
        graphics.pose().pushMatrix();
        graphics.pose().scale(rendererScale, rendererScale);
    }

    public static void popRendererScale(GuiGraphicsExtractor graphics) {
        graphics.pose().popMatrix();
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
