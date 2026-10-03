package dev.carbon.client.ui.render;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/** Registration for Carbon's retained SDF glass pipeline. */
public final class CarbonRenderPipelines {
    private static final Identifier PIPELINE_ID = Identifier.fromNamespaceAndPath(
            "carbonclient", "pipeline/carbon_glass");
    private static final Identifier SHADER_ID = Identifier.fromNamespaceAndPath(
            "carbonclient", "core/carbon_glass");

    private static RenderPipeline glassPipeline;

    private CarbonRenderPipelines() {
    }

    public static synchronized void register() {
        if (glassPipeline != null) {
            return;
        }
        glassPipeline = RenderPipelines.register(
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

    public static RenderPipeline glass() {
        return glassPipeline;
    }
}
