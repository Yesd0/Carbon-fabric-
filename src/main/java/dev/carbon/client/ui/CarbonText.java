package dev.carbon.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Readable Minecraft-native text by default, with optional bundled Inter and cached tracked layouts. */
public final class CarbonText {
    public static final float FONT_BASE_SIZE = 16.0f;
    private static final float TEXT_SIZE_BOOST = 1.18f;
    private static final int COMPONENT_CACHE_LIMIT = 768;
    private static final int TRACKED_CACHE_LIMIT = 384;
    private static volatile CarbonUiState.Typeface typeface = CarbonUiState.Typeface.MINECRAFT;

    private static final Map<Weight, Map<String, Component>> COMPONENTS = new EnumMap<>(Weight.class);
    private static final Map<Weight, EnumMap<Tracking, Map<String, TrackedLayout>>> TRACKED_LAYOUTS =
            new EnumMap<>(Weight.class);

    static {
        for (Weight weight : Weight.values()) {
            COMPONENTS.put(weight, boundedCache(COMPONENT_CACHE_LIMIT));
            EnumMap<Tracking, Map<String, TrackedLayout>> layouts = new EnumMap<>(Tracking.class);
            for (Tracking tracking : Tracking.values()) {
                layouts.put(tracking, boundedCache(TRACKED_CACHE_LIMIT));
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
        Style style = typeface == CarbonUiState.Typeface.INTER
                ? Style.EMPTY.withFont(new FontDescription.Resource(weight.fontId))
                : Style.EMPTY.withFont(FontDescription.DEFAULT);
        if (typeface == CarbonUiState.Typeface.MINECRAFT
                && (weight == Weight.SEMIBOLD || weight == Weight.BOLD)) {
            style = style.withBold(true);
        }
        Component created = Component.literal(text).withStyle(style);
        cache.put(text, created);
        return created;
    }

    /** Use Minecraft's proven UI face by default; Inter remains opt-in and has a vanilla fallback provider. */
    public static void setTypeface(CarbonUiState.Typeface nextTypeface) {
        CarbonUiState.Typeface next = nextTypeface == null
                ? CarbonUiState.Typeface.MINECRAFT : nextTypeface;
        if (typeface == next) {
            return;
        }
        typeface = next;
        for (Map<String, Component> cache : COMPONENTS.values()) {
            cache.clear();
        }
        for (EnumMap<Tracking, Map<String, TrackedLayout>> byTracking : TRACKED_LAYOUTS.values()) {
            for (Map<String, TrackedLayout> cache : byTracking.values()) {
                cache.clear();
            }
        }
    }

    public static CarbonUiState.Typeface typeface() {
        return typeface;
    }

    /** Draw one cached Carbon UI component in design pixels; the caller owns the frame scale transform. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, String text, Weight weight,
                            float designSize, float x, float y, int color, boolean shadow) {
        float scale = snappedTextScale(designSize);
        graphics.pose().pushMatrix();
        try {
            graphics.pose().translate(UiScale.snap(x), UiScale.snap(y));
            graphics.pose().scale(scale, scale);
            graphics.text(font, component(text, weight), 0, 0, color, shadow);
        } finally {
            graphics.pose().popMatrix();
        }
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
        try {
            graphics.pose().translate(UiScale.snap(x), UiScale.snap(y));
            graphics.pose().scale(scale, scale);
            for (int index = 0; index < layout.glyphs.length; index++) {
                graphics.text(font, layout.glyphs[index], Math.round(layout.offsets[index]), 0, color, false);
            }
        } finally {
            graphics.pose().popMatrix();
        }
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

    /**
     * Snap the final Inter size in physical pixels before converting it to Minecraft's 16px
     * provider space. The small optical boost compensates for Inter's lower apparent x-height
     * beside vanilla UI text, while the 7px floor keeps labels readable at the minimum UI scale.
     */
    private static float snappedTextScale(float designSize) {
        if (designSize <= 0.0f) {
            return 0.0f;
        }
        float uiScale = Math.max(0.001f, UiScale.uiScale());
        float physicalSize = Math.max(7.0f,
                Math.round(designSize * TEXT_SIZE_BOOST * uiScale));
        return physicalSize / (FONT_BASE_SIZE * uiScale);
    }

    private static <V> Map<String, V> boundedCache(int maximumSize) {
        return new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, V> eldest) {
                return size() > maximumSize;
            }
        };
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
