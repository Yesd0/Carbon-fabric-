package dev.carbon.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.LinkedHashMap;
import java.util.Map;

/** Cached helpers for the Carbon display font; text components are reused by value. */
public final class CarbonText {
    private static final Identifier DISPLAY_FONT = Identifier.fromNamespaceAndPath("carbonclient", "carbon_display");
    private static final int CACHE_LIMIT = 384;
    private static final Map<String, Component> COMPONENTS = new LinkedHashMap<>(128, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Component> eldest) {
            return size() > CACHE_LIMIT;
        }
    };

    private CarbonText() {
    }

    public static Component component(String text) {
        Component cached = COMPONENTS.get(text);
        if (cached != null) {
            return cached;
        }
        Component created = Component.literal(text).withStyle(
                Style.EMPTY.withFont(new FontDescription.Resource(DISPLAY_FONT)));
        COMPONENTS.put(text, created);
        return created;
    }

    public static void draw(GuiGraphicsExtractor graphics, Font font, String text,
                            int x, int y, int color, boolean shadow) {
        graphics.text(font, component(text), x, y, color, shadow);
    }

    public static void centered(GuiGraphicsExtractor graphics, Font font, String text,
                                int centerX, int y, int color, boolean shadow) {
        Component component = component(text);
        int x = centerX - font.width(component) / 2;
        graphics.text(font, component, x, y, color, shadow);
    }

    public static int width(Font font, String text) {
        return font.width(component(text));
    }
}
