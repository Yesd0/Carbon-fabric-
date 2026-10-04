package dev.carbon.client.ui;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Loads and draws Carbon's bundled Lucide atlas using Minecraft's GUI texture pipeline. */
public final class CarbonIcons {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath(
            "carbonclient", "textures/gui/icons.png");
    private static final Identifier INDEX = Identifier.fromNamespaceAndPath(
            "carbonclient", "textures/gui/icons.json");
    private static final Set<String> REQUIRED = Set.of(
            "layout-grid", "layout-dashboard", "user", "settings", "search", "x", "keyboard",
            "mouse-pointer-click", "gauge", "zoom-in", "shield", "flask-conical", "eye", "map-pin",
            "crosshair", "blend", "sliders-horizontal");
    private static final Map<String, IconRect> ICONS = new HashMap<>(24);

    private static int atlasWidth;
    private static int atlasHeight;
    private static boolean attempted;
    private static boolean loaded;

    private CarbonIcons() {
    }

    public static void load() {
        if (attempted) {
            return;
        }
        attempted = true;
        try (BufferedReader reader = Minecraft.getInstance().getResourceManager().openAsReader(INDEX)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            atlasWidth = root.get("width").getAsInt();
            atlasHeight = root.get("height").getAsInt();
            JsonObject icons = root.getAsJsonObject("icons");
            for (Map.Entry<String, JsonElement> entry : icons.entrySet()) {
                JsonObject bounds = entry.getValue().getAsJsonObject();
                ICONS.put(entry.getKey(), new IconRect(
                        bounds.get("x").getAsInt(),
                        bounds.get("y").getAsInt(),
                        bounds.get("width").getAsInt(),
                        bounds.get("height").getAsInt()));
            }
            for (String required : REQUIRED) {
                if (!ICONS.containsKey(required)) {
                    LOGGER.error("Carbon icon atlas index is missing icon '{}'; text labels remain available", required);
                    return;
                }
            }
            loaded = atlasWidth > 0 && atlasHeight > 0;
            if (!loaded) {
                LOGGER.error("Carbon icon atlas index has invalid dimensions; text labels remain available");
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Carbon icon atlas could not be loaded; text labels remain available", failure);
        }
    }

    /** Draws an atlas entry at a pixel-snapped design-pixel position and size. */
    public static void drawDesign(GuiGraphicsExtractor graphics, String name, float x, float y,
                                  float size, int tint) {
        load();
        if (!loaded) {
            return;
        }
        IconRect icon = ICONS.get(name);
        if (icon == null) {
            LOGGER.warn("Carbon icon atlas does not contain icon '{}'; text fallback will be used", name);
            return;
        }
        float snappedX = UiScale.snap(x);
        float snappedY = UiScale.snap(y);
        float snappedSize = Math.max(1.0f / UiScale.uiScale(), UiScale.snap(size));
        int baseSize = Math.max(1, Math.round(size));

        graphics.pose().pushMatrix();
        graphics.pose().translate(snappedX, snappedY);
        graphics.pose().scale(snappedSize / baseSize, snappedSize / baseSize);
        graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS, 0, 0,
                icon.x(), icon.y(), baseSize, baseSize, icon.width(), icon.height(),
                atlasWidth, atlasHeight, tint);
        graphics.pose().popMatrix();
    }

    /** Draws an icon in ordinary Minecraft GUI coordinates. */
    public static void drawGui(GuiGraphicsExtractor graphics, String name, int x, int y,
                               int size, int tint) {
        load();
        if (!loaded) {
            return;
        }
        IconRect icon = ICONS.get(name);
        if (icon == null) {
            LOGGER.warn("Carbon icon atlas does not contain icon '{}'; text fallback will be used", name);
            return;
        }
        int targetSize = Math.max(1, size);
        graphics.blit(RenderPipelines.GUI_TEXTURED, ATLAS, x, y,
                icon.x(), icon.y(), targetSize, targetSize, icon.width(), icon.height(),
                atlasWidth, atlasHeight, tint);
    }

    public static int iconCount() {
        load();
        return ICONS.size();
    }

    private record IconRect(int x, int y, int width, int height) {
    }
}
