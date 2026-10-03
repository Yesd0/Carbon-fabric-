package dev.carbon.client.ui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.carbon.client.ui.CarbonText;
import dev.carbon.client.ui.UiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Carbon's retained SDF glass renderer. There is deliberately no solid-fill fallback. */
public final class CarbonGlass {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final float MODE_STEP = 100000.0f;
    private static final int MODE_MAIN = 0;
    private static final int MODE_CARD_OFF = 1;
    private static final int MODE_CARD_ON = 2;
    private static final int MODE_LEGACY_TINT = 3;
    private static final int MODE_SHADOW_MAIN = 4;
    private static final int MODE_SHADOW_CARD = 5;
    private static final int MODE_GLOW_CARD = 6;
    private static final int SCALE_STEP = 100;
    private static final int ALPHA_LIMIT = 0x8C;

    private static String failureMessage;
    private static String failureLabel;

    private CarbonGlass() {
    }

    public static void drawPanel(GuiGraphicsExtractor graphics, float x, float y, float width, float height,
                                 float radius, Style style) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        UiScale.update(Minecraft.getInstance());
        RenderPipeline pipeline = CarbonRenderPipelines.glass();
        if (pipeline == null) {
            reportFailure("SDF glass shader is unavailable", null);
            return;
        }

        float left = UiScale.snap(x);
        float top = UiScale.snap(y);
        float snappedWidth = Math.max(1.0f / UiScale.uiScale(), UiScale.snap(width));
        float snappedHeight = Math.max(1.0f / UiScale.uiScale(), UiScale.snap(height));
        float snappedRadius = Math.max(0.0f, UiScale.snap(radius));
        float scale = UiScale.uiScale();

        if (style == Style.MAIN) {
            submitShadow(graphics, pipeline, left, top, snappedWidth, snappedHeight,
                    snappedRadius, scale, MODE_SHADOW_MAIN, 60.0f, 24.0f);
        } else {
            submitShadow(graphics, pipeline, left, top, snappedWidth, snappedHeight,
                    snappedRadius, scale, MODE_SHADOW_CARD, 24.0f, 8.0f);
            if (style == Style.CARD_ON) {
                submitShadow(graphics, pipeline, left, top, snappedWidth, snappedHeight,
                        snappedRadius, scale, MODE_GLOW_CARD, 20.0f, 0.0f);
            }
        }

        int mode = switch (style) {
            case MAIN -> MODE_MAIN;
            case CARD_OFF -> MODE_CARD_OFF;
            case CARD_ON -> MODE_CARD_ON;
        };
        submit(graphics, pipeline, left, top, snappedWidth, snappedHeight,
                snappedRadius, scale, mode, 0xFFFFFFFF);
    }

    /** Design-pixel translucent helper for small controls inside a Carbon design-space transform. */
    public static void drawTintedRectDesign(GuiGraphicsExtractor graphics, float x, float y,
                                            float width, float height, float radius, int argb) {
        if (width <= 0.0f || height <= 0.0f || (argb >>> 24) == 0) {
            return;
        }
        UiScale.update(Minecraft.getInstance());
        RenderPipeline pipeline = CarbonRenderPipelines.glass();
        if (pipeline == null) {
            reportFailure("SDF glass shader is unavailable", null);
            return;
        }
        int alpha = Math.min((argb >>> 24) & 0xFF, ALPHA_LIMIT);
        int color = (alpha << 24) | (argb & 0x00FFFFFF);
        float left = UiScale.snap(x);
        float top = UiScale.snap(y);
        float snappedWidth = Math.max(1.0f / UiScale.uiScale(), UiScale.snap(width));
        float snappedHeight = Math.max(1.0f / UiScale.uiScale(), UiScale.snap(height));
        float snappedRadius = Math.max(0.0f, UiScale.snap(radius));
        submit(graphics, pipeline, left, top, snappedWidth, snappedHeight,
                snappedRadius, UiScale.uiScale(), MODE_LEGACY_TINT, color);
    }

    /** Glass-only compatibility primitive for the earlier Carbon controls. */
    public static void drawTintedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                      float radius, int argb) {
        if (width <= 0 || height <= 0 || (argb >>> 24) == 0) {
            return;
        }
        RenderPipeline pipeline = CarbonRenderPipelines.glass();
        if (pipeline == null) {
            reportFailure("SDF glass shader is unavailable", null);
            return;
        }
        UiScale.update(Minecraft.getInstance());
        int alpha = Math.min((argb >>> 24) & 0xFF, ALPHA_LIMIT);
        int color = (alpha << 24) | (argb & 0x00FFFFFF);
        submit(graphics, pipeline, x, y, width, height, radius, UiScale.guiScale(),
                MODE_LEGACY_TINT, color);
    }

    /** Glass-only border helper for the older menu while Part B is being built. */
    public static void outlineTintedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                         float radius, int borderArgb, int fillArgb) {
        if (width <= 1 || height <= 1) {
            drawTintedRect(graphics, x, y, width, height, radius, borderArgb);
            return;
        }
        drawTintedRect(graphics, x, y, width, height, radius, borderArgb);
        drawTintedRect(graphics, x + 1, y + 1, width - 2, height - 2,
                Math.max(0.0f, radius - 1.0f), fillArgb);
    }

    public static String failureMessage() {
        return failureMessage;
    }

    public static void clearFailure() {
        failureMessage = null;
        failureLabel = null;
    }

    public static void clearFailure(String reason) {
        if (reason != null && reason.equals(failureMessage)) {
            failureMessage = null;
            failureLabel = null;
        }
    }

    public static void reportFailure(String reason, Throwable failure) {
        if (reason == null || reason.isBlank()) {
            reason = "unknown rendering failure";
        }
        if (reason.equals(failureMessage)) {
            return;
        }
        failureMessage = reason;
        failureLabel = "Carbon render failed: " + reason;
        if (failure == null) {
            LOGGER.error("Carbon render failed: {}", reason);
        } else {
            LOGGER.error("Carbon render failed: {}", reason, failure);
        }
    }

    public static void drawFailureLabel(GuiGraphicsExtractor graphics) {
        if (failureLabel == null) {
            return;
        }
        CarbonText.drawDesign(graphics, Minecraft.getInstance().font, failureLabel,
                CarbonText.Weight.SEMIBOLD, 13.0f, 24.0f, 24.0f, 0xFFFF6670, false);
    }

    /** GUI-coordinate failure label for legacy Carbon screens without a design-space transform. */
    public static void drawFailureLabelGui(GuiGraphicsExtractor graphics) {
        if (failureLabel == null) {
            return;
        }
        CarbonText.draw(graphics, Minecraft.getInstance().font, failureLabel,
                24, 24, 0xFFFF6670, false);
    }

    private static void submitShadow(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
                                     float x, float y, float width, float height, float radius, float scale,
                                     int mode, float spread, float offsetY) {
        float expansion = UiScale.snap(spread);
        submit(graphics, pipeline,
                x - expansion, y + UiScale.snap(offsetY) - expansion,
                width + expansion * 2.0f, height + expansion * 2.0f,
                radius, scale, mode, 0xFFFFFFFF);
    }

    private static void submit(GuiGraphicsExtractor graphics, RenderPipeline pipeline,
                               float x, float y, float width, float height, float radius,
                               float pixelScale, int mode, int argb) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        int scaleCode = Math.round(pixelScale * SCALE_STEP);
        float shapeData = mode * MODE_STEP + scaleCode * SCALE_STEP + radius;
        graphics.submitGuiElementRenderState(
                new CarbonShapeState(graphics.pose(), pipeline, x, y, width, height, shapeData, argb)
        );
    }

    public enum Style {
        MAIN,
        CARD_OFF,
        CARD_ON
    }

    private static final class CarbonShapeState implements GuiElementRenderState {
        private final Matrix3x2f pose;
        private final RenderPipeline pipeline;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float shapeData;
        private final int argb;
        private final ScreenRectangle bounds;

        private CarbonShapeState(Matrix3x2f pose, RenderPipeline pipeline, float x, float y,
                                 float width, float height, float shapeData, int argb) {
            this.pose = new Matrix3x2f(pose);
            this.pipeline = pipeline;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.shapeData = shapeData;
            this.argb = argb;
            this.bounds = new ScreenRectangle((int) Math.floor(x), (int) Math.floor(y),
                    Math.max(1, (int) Math.ceil(width)), Math.max(1, (int) Math.ceil(height)))
                    .transformMaxBounds(this.pose);
        }

        @Override
        public ScreenRectangle bounds() {
            return bounds;
        }

        @Override
        public ScreenRectangle scissorArea() {
            return null;
        }

        @Override
        public RenderPipeline pipeline() {
            return pipeline;
        }

        @Override
        public TextureSetup textureSetup() {
            return TextureSetup.noTexture();
        }

        @Override
        public void buildVertices(VertexConsumer vertices) {
            addVertex(vertices, x, y, 0.0f, 0.0f);
            addVertex(vertices, x, y + height, 0.0f, 1.0f);
            addVertex(vertices, x + width, y + height, 1.0f, 1.0f);
            addVertex(vertices, x + width, y, 1.0f, 0.0f);
        }

        private void addVertex(VertexConsumer vertices, float localX, float localY, float u, float v) {
            float transformedX = pose.m00() * localX + pose.m10() * localY + pose.m20();
            float transformedY = pose.m01() * localX + pose.m11() * localY + pose.m21();
            vertices.addVertex(transformedX, transformedY, shapeData)
                    .setUv(u, v)
                    .setColor(argb);
        }
    }
}
