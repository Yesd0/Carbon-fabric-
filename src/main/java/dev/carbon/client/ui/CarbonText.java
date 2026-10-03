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

/** Cached Inter components and design-pixel text placement for Carbon screens. */
public final class CarbonText {
    public static final float FONT_BASE_SIZE = 16.0f;
    private static final float LEGACY_TEXT_SIZE = 12.0f;
    private static final int CACHE_LIMIT = 768;
    private static final Map<Weight, Map<String, Component>> COMPONENTS = new EnumMap<>(Weight.class);

    static {
        for (Weight weight : Weight.values()) {
            COMPONENTS.put(weight, new HashMap<>(128));
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
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(text, created);
        return created;
    }

    /** Compatibility entry point used by the existing Carbon controls in GUI coordinates. */
    public static void draw(GuiGraphicsExtractor graphics, Font font, String text,
                            int x, int y, int color, boolean shadow) {
        drawAtScale(graphics, font, text, Weight.REGULAR,
                LEGACY_TEXT_SIZE / FONT_BASE_SIZE, x, y, color, shadow);
    }

    public static void centered(GuiGraphicsExtractor graphics, Font font, String text,
                                int centerX, int y, int color, boolean shadow) {
        float textWidth = font.width(component(text)) * (LEGACY_TEXT_SIZE / FONT_BASE_SIZE);
        draw(graphics, font, text, Math.round(centerX - textWidth * 0.5f), y, color, shadow);
    }

    /** Draws text at a design-pixel size, with its origin and physical font size pixel-snapped. */
    public static void drawDesign(GuiGraphicsExtractor graphics, Font font, String text,
                                  Weight weight, float size, float x, float y, int color, boolean shadow) {
        float uiScale = UiScale.uiScale();
        float physicalSize = Math.max(1.0f, Math.round(size * uiScale));
        float scale = physicalSize / (FONT_BASE_SIZE * uiScale);
        drawAtScale(graphics, font, text, weight, scale, UiScale.snap(x), UiScale.snap(y), color, shadow);
    }

    public static void centeredDesign(GuiGraphicsExtractor graphics, Font font, String text,
                                      Weight weight, float size, float centerX, float y,
                                      int color, boolean shadow) {
        float textWidth = widthDesign(font, text, weight, size);
        drawDesign(graphics, font, text, weight, size,
                centerX - textWidth * 0.5f, y, color, shadow);
    }

    public static float widthDesign(Font font, String text, Weight weight, float size) {
        float uiScale = UiScale.uiScale();
        float physicalSize = Math.max(1.0f, Math.round(size * uiScale));
        float scale = physicalSize / (FONT_BASE_SIZE * uiScale);
        return font.width(component(text, weight)) * scale;
    }

    /** Width matching the 12px compatibility draw size used by the existing Carbon controls. */
    public static int width(Font font, String text) {
        return Math.round(font.width(component(text)) * (LEGACY_TEXT_SIZE / FONT_BASE_SIZE));
    }

    private static void drawAtScale(GuiGraphicsExtractor graphics, Font font, String text,
                                    Weight weight, float scale, float x, float y,
                                    int color, boolean shadow) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(font, component(text, weight), 0, 0, color, shadow);
        graphics.pose().popMatrix();
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
