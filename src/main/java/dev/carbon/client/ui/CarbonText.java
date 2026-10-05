package dev.carbon.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/** Bundled Inter TTF text helpers with cached components and per-codepoint tracked layouts. */
public final class CarbonText {
    public static final float FONT_BASE_SIZE = 16.0f;

    private static final Map<Weight, Map<String, Component>> COMPONENTS = new EnumMap<>(Weight.class);
    private static final Map<Weight, EnumMap<Tracking, Map<String, TrackedLayout>>> TRACKED_LAYOUTS =
            new EnumMap<>(Weight.class);

    static {
        for (Weight weight : Weight.values()) {
            COMPONENTS.put(weight, new HashMap<>(128));
            EnumMap<Tracking, Map<String, TrackedLayout>> layouts = new EnumMap<>(Tracking.class);
            for (Tracking tracking : Tracking.values()) {
                layouts.put(tracking, new HashMap<>(64));
            }
            TRACKED_LAYOUTS.put(weight, layouts);
        }
    }

    private CarbonText() {
    }

    public static Component component(String text) {
        return component(text, Weight.REGULAR);
    }

    public static Component component(String text, Weight weight) {
        Map<String, Component> cache = COMPONENTS.get(weight);
        Component cached = cache.get(text);
        if (cached != null) {
            return cached;
        }
        Component created = Component.literal(text).withStyle(
                Style.EMPTY.withFont(new FontDescription.Resource(weight.fontId)));
        cache.put(text, created);
        return created;
    }

    /** Draw one cached Inter component in design pixels; the caller owns the frame scale transform. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, String text, Weight weight,
                            float designSize, float x, float y, int color, boolean shadow) {
        float scale = snappedTextScale(designSize);
        graphics.pose().pushMatrix();
        graphics.pose().translate(UiScale.snap(x), UiScale.snap(y));
        graphics.pose().scale(scale, scale);
        graphics.text(font, component(text, weight), 0, 0, color, shadow);
        graphics.pose().popMatrix();
    }

    public static void centered(GuiGraphicsExtractor graphics, Font font, String text, Weight weight,
                                float designSize, float centerX, float y, int color, boolean shadow) {
        float textWidth = width(font, text, weight, designSize);
        draw(graphics, font, text, weight, designSize, centerX - textWidth * 0.5f,
                y, color, shadow);
    }

    public static float width(Font font, String text, Weight weight, float designSize) {
        float scale = snappedTextScale(designSize);
        return font.width(component(text, weight)) * scale;
    }

    /** Draw uppercase UI labels one codepoint at a time so the requested tracking is stable. */
    public static void drawTracked(GuiGraphicsExtractor graphics, Font font, String text, Weight weight,
                                   float designSize, float x, float y, int color, Tracking tracking) {
        if (text.isEmpty()) {
            return;
        }
        TrackedLayout layout = trackedLayout(font, text, weight, tracking);
        float scale = snappedTextScale(designSize);
        graphics.pose().pushMatrix();
        graphics.pose().translate(UiScale.snap(x), UiScale.snap(y));
        graphics.pose().scale(scale, scale);
        for (int index = 0; index < layout.glyphs.length; index++) {
            graphics.text(font, layout.glyphs[index], Math.round(layout.offsets[index]), 0, color, false);
        }
        graphics.pose().popMatrix();
    }

    public static void centeredTracked(GuiGraphicsExtractor graphics, Font font, String text, Weight weight,
                                       float designSize, float centerX, float y, int color,
                                       Tracking tracking) {
        float textWidth = trackedWidth(font, text, weight, designSize, tracking);
        drawTracked(graphics, font, text, weight, designSize,
                centerX - textWidth * 0.5f, y, color, tracking);
    }

    public static float trackedWidth(Font font, String text, Weight weight, float designSize,
                                     Tracking tracking) {
        return trackedLayout(font, text, weight, tracking).width * snappedTextScale(designSize);
    }

    private static TrackedLayout trackedLayout(Font font, String text, Weight weight, Tracking tracking) {
        Map<String, TrackedLayout> cache = TRACKED_LAYOUTS.get(weight).get(tracking);
        TrackedLayout cached = cache.get(text);
        if (cached != null) {
            return cached;
        }

        int[] codepoints = text.codePoints().toArray();
        Component[] glyphs = new Component[codepoints.length];
        float[] offsets = new float[codepoints.length];
        float cursor = 0.0f;
        float trackingAdvance = FONT_BASE_SIZE * tracking.em;
        for (int index = 0; index < codepoints.length; index++) {
            String glyphText = new String(Character.toChars(codepoints[index]));
            Component glyph = component(glyphText, weight);
            glyphs[index] = glyph;
            offsets[index] = cursor;
            cursor += font.width(glyph);
            if (index + 1 < codepoints.length) {
                cursor += trackingAdvance;
            }
        }

        TrackedLayout created = new TrackedLayout(glyphs, offsets, cursor);
        cache.put(text, created);
        return created;
    }

    /** Size snapping is in physical pixels; renderer/UI-scale conversion is applied by UiScale. */
    private static float snappedTextScale(float designSize) {
        float scale = Math.max(0.0f, UiScale.uiScale());
        if (scale == 0.0f) {
            return designSize / FONT_BASE_SIZE;
        }
        float physicalSize = Math.max(1.0f, Math.round(designSize * scale));
        return physicalSize / (FONT_BASE_SIZE * scale);
    }

    private record TrackedLayout(Component[] glyphs, float[] offsets, float width) {
    }

    public enum Tracking {
        NONE(0.0f),
        WORDMARK(0.14f),
        LABEL(0.18f),
        WIDE(0.24f);

        private final float em;

        Tracking(float em) {
            this.em = em;
        }
    }

    public enum Weight {
        REGULAR("carbon_inter_regular"),
        MEDIUM("carbon_inter_medium"),
        SEMIBOLD("carbon_inter_semibold"),
        BOLD("carbon_inter_bold");

        private final Identifier fontId;

        Weight(String path) {
            this.fontId = Identifier.fromNamespaceAndPath("carbonclient", path);
        }
    }
}
