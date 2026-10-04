package dev.carbon.client.ui.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.carbon.client.ui.UiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Matrix3x2f;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Solid SDF-backed Carbon shapes, with smooth subpixel coverage and no backdrop effects. */
public final class CarbonShapes {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath(
            "carbonclient", "pipeline/carbon_rounded_rect");
    private static final Identifier SHADER_ID = Identifier.fromNamespaceAndPath(
            "carbonclient", "core/carbon_rounded_rect");

    private static RenderPipeline roundedRectPipeline;
    private static boolean registrationAttempted;
    private static boolean fallbackLogged;

    private CarbonShapes() {
    }

    /** Registers the tiny solid-color GUI pipeline before the first screen is opened. */
    public static synchronized void initialize() {
        if (registrationAttempted) {
            return;
        }
        registrationAttempted = true;
        roundedRectPipeline = RenderPipelines.register(
                RenderPipeline.builder(RenderPipelines.GUI_SNIPPET)
                        .withLocation(PIPELINE_ID)
                        .withVertexShader(SHADER_ID)
                        .withFragmentShader(SHADER_ID)
                        .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                        .withPrimitiveTopology(PrimitiveTopology.QUADS)
                        .withUsePipelineDrawModeForGui(true)
                        .build()
        );
    }

    /** Draws an opaque rounded rectangle; only its antialiased outer edge blends with the scene. */
    public static void drawRounded(GuiGraphicsExtractor graphics, float x, float y,
                                   float width, float height, float radius, int argb) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }
        UiScale.update(Minecraft.getInstance());
        RenderPipeline pipeline = roundedRectPipeline;
        if (pipeline == null) {
            drawPixelAlignedFallback(graphics, x, y, width, height, radius, argb);
            if (!fallbackLogged) {
                fallbackLogged = true;
                LOGGER.error("Carbon rounded-rectangle pipeline is unavailable; using the native fill fallback");
            }
            return;
        }

        float physicalRadius = Math.max(0.0f, radius * UiScale.guiScale());
        graphics.guiRenderState.addGuiElement(
                new RoundedRectState(graphics.pose(), pipeline, x, y, width, height, physicalRadius, argb)
        );
    }

    private static void drawPixelAlignedFallback(GuiGraphicsExtractor graphics, float x, float y,
                                                  float width, float height, float radius, int color) {
        int left = Math.round(x);
        int top = Math.round(y);
        int right = Math.round(x + width);
        int bottom = Math.round(y + height);
        int rectWidth = Math.max(0, right - left);
        int rectHeight = Math.max(0, bottom - top);
        int corner = Math.max(0, Math.min(Math.round(radius), Math.min(rectWidth, rectHeight) / 2));
        if (corner == 0) {
            graphics.fill(left, top, right, bottom, color);
            return;
        }
        for (int row = 0; row < corner; row++) {
            double distance = corner - row - 0.5;
            int inset = Math.max(0, corner - (int) Math.floor(Math.sqrt(Math.max(0.0,
                    corner * corner - distance * distance))));
            int spanLeft = left + inset;
            int spanRight = right - inset;
            if (spanRight > spanLeft) {
                graphics.fill(spanLeft, top + row, spanRight, top + row + 1, color);
                graphics.fill(spanLeft, bottom - row - 1, spanRight, bottom - row, color);
            }
        }
        int middleTop = top + corner;
        int middleBottom = bottom - corner;
        if (middleBottom > middleTop) {
            graphics.fill(left, middleTop, right, middleBottom, color);
        }
    }

    private static final class RoundedRectState implements GuiElementRenderState {
        private final Matrix3x2f pose;
        private final RenderPipeline pipeline;
        private final float x;
        private final float y;
        private final float width;
        private final float height;
        private final float radius;
        private final int argb;
        private final ScreenRectangle bounds;

        private RoundedRectState(Matrix3x2f pose, RenderPipeline pipeline, float x, float y,
                                 float width, float height, float radius, int argb) {
            this.pose = new Matrix3x2f(pose);
            this.pipeline = pipeline;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.radius = radius;
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
            vertices.addVertex(transformedX, transformedY, radius)
                    .setUv(u, v)
                    .setColor(argb);
        }
    }
}
