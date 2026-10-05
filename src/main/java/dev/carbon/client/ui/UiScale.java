package dev.carbon.client.ui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Keeps Carbon layout in 1920x1080 design pixels and converts only at the renderer boundary. */
public final class UiScale {
    public static final float DESIGN_WIDTH = 1920.0f;
    public static final float DESIGN_HEIGHT = 1080.0f;
    public static final float MIN_SCALE = 0.6f;
    public static final float MAX_SCALE = 1.6f;

    private static int framebufferWidth = 1920;
    private static int framebufferHeight = 1080;
    private static float guiScale = 1.0f;
    private static float uiScale = 1.0f;
    private static float rendererScale = 1.0f;
    private static float designWidth = DESIGN_WIDTH;
    private static float designHeight = DESIGN_HEIGHT;
    private static boolean initialized;

    private UiScale() {
    }

    /**
     * Refresh cached framebuffer metrics. The UI scale is deliberately resolution-only:
     * clamp(min(fbWidth / 1920, fbHeight / 1080), 0.6, 1.6).
     */
    public static boolean update(Minecraft minecraft) {
        Window window = minecraft.getWindow();
        int nextFramebufferWidth = Math.max(1, window.getWidth());
        int nextFramebufferHeight = Math.max(1, window.getHeight());
        float nextGuiScale = Math.max(1.0f, (float) window.getGuiScale());
        boolean changed = !initialized
                || nextFramebufferWidth != framebufferWidth
                || nextFramebufferHeight != framebufferHeight
                || Float.compare(nextGuiScale, guiScale) != 0;
        if (!changed) {
            return false;
        }

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
        return true;
    }

    public static float uiScale() {
        return uiScale;
    }

    public static float guiScale() {
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

    /** Converts a design-pixel coordinate into Minecraft GUI renderer coordinates. */
    public static float toRenderer(float designPx) {
        return designPx * rendererScale;
    }

    /** Converts an input coordinate from Minecraft GUI space back to design pixels. */
    public static float toDesign(double guiCoordinate) {
        return (float) (guiCoordinate / rendererScale);
    }

    /** Snaps a design coordinate or size to a whole physical framebuffer pixel. */
    public static float snap(float designPx) {
        return Math.round(designPx * uiScale) / uiScale;
    }

    /** Snaps an ordinary Minecraft GUI coordinate to a whole physical framebuffer pixel. */
    public static float snapGui(float guiPx) {
        return Math.round(guiPx * guiScale) / guiScale;
    }

    public static int framebufferPixels(float designPx) {
        return Math.round(designPx * uiScale);
    }

    /** Apply once around a Carbon design-pixel draw pass; layout values remain unscaled. */
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
