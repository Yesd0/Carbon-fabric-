package dev.carbon.client.ui.render;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;

/** SDF-backed primitives recorded into Minecraft's GUI render state. */
public final class CarbonRenderer {
    private CarbonRenderer() {
    }

    public static void roundedRect(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                                   float radius, int argb) {
        if (width <= 0 || height <= 0 || (argb >>> 24) == 0) {
            return;
        }

        float safeRadius = Math.max(0.0f, Math.min(radius, Math.min(width, height) * 0.5f));
        RenderPipeline pipeline = CarbonRenderPipelines.sdf();
        if (pipeline == null) {
            graphics.fill(x, y, x + width, y + height, argb);
            return;
        }

        graphics.guiRenderState.addGuiElement(
                new SdfRectState(graphics.pose(), pipeline, x, y, width, height, safeRadius, argb)
        );
    }

    public static void panel(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int argb) {
        roundedRect(graphics, x, y, width, height, 12.0f, argb);
    }

    /** Draws an outer SDF border followed by an inset fill. */
    public static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height,
                               float radius, int borderArgb, int fillArgb) {
        if (width <= 1 || height <= 1) {
            roundedRect(graphics, x, y, width, height, radius, borderArgb);
            return;
        }
        roundedRect(graphics, x, y, width, height, radius, borderArgb);
        roundedRect(graphics, x + 1, y + 1, width - 2, height - 2,
                Math.max(0.0f, radius - 1.0f), fillArgb);
    }

    private static final class SdfRectState implements GuiElementRenderState {
        private final Matrix3x2f pose;
        private final RenderPipeline pipeline;
        private final int x;
        private final int y;
        private final int width;
        private final int height;
        private final float radius;
        private final int argb;
        private final ScreenRectangle bounds;

        private SdfRectState(Matrix3x2f pose, RenderPipeline pipeline, int x, int y, int width, int height,
                             float radius, int argb) {
            this.pose = new Matrix3x2f(pose);
            this.pipeline = pipeline;
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.radius = radius;
            this.argb = argb;
            this.bounds = new ScreenRectangle(x, y, width, height).transformMaxBounds(this.pose);
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
            float left = x;
            float top = y;
            float right = x + width;
            float bottom = y + height;
            addVertex(vertices, left, top, 0.0f, 0.0f);
            addVertex(vertices, left, bottom, 0.0f, 1.0f);
            addVertex(vertices, right, bottom, 1.0f, 1.0f);
            addVertex(vertices, right, top, 1.0f, 0.0f);
        }

        private void addVertex(VertexConsumer vertices, float x, float y, float u, float v) {
            float transformedX = pose.m00() * x + pose.m10() * y + pose.m20();
            float transformedY = pose.m01() * x + pose.m11() * y + pose.m21();
            vertices.addVertex(transformedX, transformedY, radius)
                    .setUv(u, v)
                    .setColor(argb);
        }
    }
}
