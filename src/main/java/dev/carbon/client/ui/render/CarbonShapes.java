package dev.carbon.client.ui.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.carbon.client.ui.CarbonTheme;
import dev.carbon.client.ui.UiScale;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** Carbon's matte SDF surface pipeline: antialiased edges, gradient fills, layered borders and shadows. */
public final class CarbonShapes {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath(
            "carbonclient", "pipeline/carbon_style_rect");
    private static final Identifier SHADER_ID = Identifier.fromNamespaceAndPath(
            "carbonclient", "core/carbon_style_rect");
    private static final int SHADOW_STEPS = 8;
    private static final int GLOW_STEPS = 5;

    private static RenderPipeline styleRectPipeline;
    private static boolean registrationAttempted;
    private static String failureReason;

    private CarbonShapes() {
    }

    /** Register once and fail closed. A broken shader never falls back to flat native rectangles. */
    public static synchronized void initialize() {
        if (registrationAttempted) {
            return;
        }
        registrationAttempted = true;
        try {
            validateShaderSource("/assets/carbonclient/shaders/core/carbon_style_rect.vsh", "vertex");
            validateShaderSource("/assets/carbonclient/shaders/core/carbon_style_rect.fsh", "fragment");
            styleRectPipeline = RenderPipelines.register(
                    RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                            .withLocation(PIPELINE_ID)
                            .withVertexShader(SHADER_ID)
                            .withFragmentShader(SHADER_ID)
                            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                            .withPrimitiveTopology(PrimitiveTopology.QUADS)
                            .withUsePipelineDrawModeForGui(true)
                            .build()
            );
            if (styleRectPipeline == null) {
                throw new IllegalStateException("Minecraft returned a null Carbon style pipeline");
            }
        } catch (Throwable failure) {
            fail(failure);
        }
    }

    public static boolean isReady() {
        return styleRectPipeline != null && failureReason == null;
    }

    /** Human-readable cause shown in the on-screen Carbon render diagnostic. */
    public static String failureReason() {
        if (failureReason != null) {
            return failureReason;
        }
        return styleRectPipeline == null ? "SDF pipeline is not initialized" : "unknown shader failure";
    }

    /** Solid or vertical-gradient rounded rectangle in design pixels. */
    public static void drawRounded(GuiGraphicsExtractor graphics, float x, float y,
                                   float width, float height, float radius, int argb) {
        drawGradientRect(graphics, x, y, width, height, radius, argb, argb);
    }

    public static void drawGradientRect(GuiGraphicsExtractor graphics, float x, float y,
                                        float width, float height, float radius,
                                        int topColor, int bottomColor) {
        drawRaw(graphics, x, y, width, height, radius, topColor, bottomColor, false);
    }

    /** Border is an SDF outer shape with an inset gradient surface; no native fill path is used. */
    public static void drawBorderedSurface(GuiGraphicsExtractor graphics, float x, float y,
                                           float width, float height, float radius, float borderWidth,
                                           int borderTop, int borderBottom,
                                           int fillTop, int fillBottom) {
        if (borderWidth <= 0.0f) {
            drawGradientRect(graphics, x, y, width, height, radius, fillTop, fillBottom);
            return;
        }
        drawGradientRect(graphics, x, y, width, height, radius, borderTop, borderBottom);
        float inset = Math.min(borderWidth, Math.min(width, height) * 0.5f);
        drawGradientRect(graphics, x + inset, y + inset,
                width - inset * 2.0f, height - inset * 2.0f,
                Math.max(0.0f, radius - inset), fillTop, fillBottom);
    }

    /** Layered translucent SDF quads produce a soft matte shadow without sampling the world. */
    public static void drawShadow(GuiGraphicsExtractor graphics, float x, float y,
                                  float width, float height, float radius,
                                  float blurRadius, float offsetY, int color) {
        if (!isReady() || blurRadius <= 0.0f) {
            return;
        }
        int steps = blurRadius >= 40.0f ? SHADOW_STEPS : blurRadius >= 16.0f ? 5 : blurRadius >= 8.0f ? 4 : 3;
        for (int step = steps - 1; step >= 1; step--) {
            float t = step / (float) steps;
            float spread = blurRadius * t;
            float inner = 1.0f - t;
            float opacity = inner * inner * 0.72f;
            int ringColor = CarbonTheme.alphaScale(color, opacity);
            drawGradientRect(graphics, x - spread, y + offsetY - spread,
                    width + spread * 2.0f, height + spread * 2.0f,
                    radius + spread, ringColor, ringColor);
        }
    }

    /** Stacked accent rings provide a low-cost green SDF glow; disabled unless called explicitly. */
    public static void drawGlow(GuiGraphicsExtractor graphics, float x, float y,
                                float width, float height, float radius,
                                float spread, int color) {
        if (!isReady() || spread <= 0.0f) {
            return;
        }
        for (int step = GLOW_STEPS - 1; step >= 1; step--) {
            float t = step / (float) GLOW_STEPS;
            float expansion = spread * t;
            float inner = 1.0f - t;
            int ringColor = CarbonTheme.alphaScale(color, inner * inner * 0.32f);
            drawGradientRect(graphics, x - expansion, y - expansion,
                    width + expansion * 2.0f, height + expansion * 2.0f,
                    radius + expansion, ringColor, ringColor);
        }
    }

    /** A restrained 1px upper-edge bevel/highlight for matte Carbon surfaces. */
    public static void drawTopHighlight(GuiGraphicsExtractor graphics, float x, float y,
                                        float width, float radius, int color) {
        float inset = Math.min(Math.max(2.0f, radius * 0.55f), width * 0.25f);
        drawGradientRect(graphics, x + inset, y + 1.0f, Math.max(0.0f, width - inset * 2.0f),
                1.0f, 0.5f, color, color);
    }

    /** Radial edge darkening drawn by the same SDF shader, with a transparent center. */
    public static void drawVignette(GuiGraphicsExtractor graphics, float x, float y,
                                    float width, float height, int color) {
        drawRaw(graphics, x, y, width, height, 0.0f, color, color, true);
    }

    /** Debug-only rectangular bounds, rendered as four thin SDF strips. */
    public static void drawOutline(GuiGraphicsExtractor graphics, float x, float y,
                                   float width, float height, float thickness, int color) {
        if (thickness <= 0.0f) {
            return;
        }
        drawGradientRect(graphics, x, y, width, thickness, thickness * 0.5f, color, color);
        drawGradientRect(graphics, x, y + height - thickness,
                width, thickness, thickness * 0.5f, color, color);
        drawGradientRect(graphics, x, y + thickness,
                thickness, Math.max(0.0f, height - thickness * 2.0f), thickness * 0.5f, color, color);
        drawGradientRect(graphics, x + width - thickness, y + thickness,
                thickness, Math.max(0.0f, height - thickness * 2.0f), thickness * 0.5f, color, color);
    }

    private static void drawRaw(GuiGraphicsExtractor graphics, float x, float y,
                                float width, float height, float radius,
                                int topColor, int bottomColor, boolean vignette) {
        if (!isReady() || width <= 0.0f || height <= 0.0f) {
            return;
        }
        float physicalRadius = Math.max(0.0f, radius) * UiScale.uiScale();
        graphics.guiRenderState.addGuiElement(new RoundedRectState(
                graphics.pose(), styleRectPipeline, x, y, width, height,
                physicalRadius, topColor, bottomColor, vignette));
    }

    private static void validateShaderSource(String classpath, String stage) throws IOException {
        try (InputStream stream = CarbonShapes.class.getResourceAsStream(classpath)) {
            if (stream == null) {
                throw new IOException("Missing Carbon " + stage + " shader resource " + classpath);
            }
            String source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            if (!source.contains("#version 330") || !source.contains("void main")
                    || !balancedBraces(source)) {
                throw new IOException("Carbon " + stage + " shader source failed its structural preflight: " + classpath);
            }
        }
    }

    private static boolean balancedBraces(String source) {
        int balance = 0;
        for (int index = 0; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value == '{') {
                balance++;
            } else if (value == '}' && --balance < 0) {
                return false;
            }
        }
        return balance == 0;
    }

    private static void fail(Throwable failure) {
        String message = failure.getMessage();
        failureReason = message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
        styleRectPipeline = null;
        LOGGER.error("Carbon render failed: {}", failureReason, failure);
    }

    private static final class RoundedRectState implements GuiElementRenderState {
        private final Matrix3x2f pose;
        private final RenderPipeline pipeline;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float radius;
        private final int topColor;
        private final int bottomColor;
        private final boolean vignette;
        private final ScreenRectangle bounds;

        private RoundedRectState(Matrix3x2f pose, RenderPipeline pipeline, float x, float y,
                                 float width, float height, float radius,
                                 int topColor, int bottomColor, boolean vignette) {
            this.pose = new Matrix3x2f(pose);
            this.pipeline = pipeline;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.radius = radius;
            this.topColor = topColor;
            this.bottomColor = bottomColor;
            this.vignette = vignette;
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
            addVertex(vertices, x, y, 0.0f, 0.0f, topColor);
            addVertex(vertices, x, y + height, 0.0f, 1.0f, bottomColor);
            addVertex(vertices, x + width, y + height, 1.0f, 1.0f, bottomColor);
            addVertex(vertices, x + width, y, 1.0f, 0.0f, topColor);
        }

        private void addVertex(VertexConsumer vertices, float localX, float localY,
                               float u, float v, int color) {
            float transformedX = pose.m00() * localX + pose.m10() * localY + pose.m20();
            float transformedY = pose.m01() * localX + pose.m11() * localY + pose.m21();
            vertices.addVertex(transformedX, transformedY, vignette ? -1.0f : radius)
                    .setUv(u, v)
                    .setColor(color);
        }
    }
}
