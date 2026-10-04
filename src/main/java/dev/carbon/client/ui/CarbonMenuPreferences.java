package dev.carbon.client.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Persistent menu-only appearance preferences, independent from module profiles. */
public final class CarbonMenuPreferences {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final float MIN_SCALE = 0.75f;
    private static final float MAX_SCALE = 1.50f;

    private final Path file;
    private boolean customFont = true;
    private float uiScale = 1.0f;

    private CarbonMenuPreferences(Path file) {
        this.file = file;
    }

    public static CarbonMenuPreferences load(Path file) {
        CarbonMenuPreferences preferences = new CarbonMenuPreferences(file);
        if (!Files.isRegularFile(file)) {
            return preferences;
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            if (root.has("customFont") && root.get("customFont").isJsonPrimitive()) {
                preferences.customFont = root.get("customFont").getAsBoolean();
            }
            if (root.has("uiScale") && root.get("uiScale").isJsonPrimitive()) {
                preferences.uiScale = clamp(root.get("uiScale").getAsFloat());
            }
        } catch (IOException | RuntimeException failure) {
            LOGGER.warn("Could not load Carbon menu preferences from {}; defaults will be used", file, failure);
        }
        return preferences;
    }

    public boolean customFont() {
        return customFont;
    }

    public float uiScale() {
        return uiScale;
    }

    public void setCustomFont(boolean customFont) {
        this.customFont = customFont;
        save();
    }

    public void setUiScale(float uiScale) {
        setUiScaleLive(uiScale);
        save();
    }

    /** Updates a dragged slider without writing a file for every input event. */
    public void setUiScaleLive(float uiScale) {
        this.uiScale = clamp(uiScale);
    }

    private void save() {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", 2);
        root.addProperty("customFont", customFont);
        root.addProperty("uiScale", uiScale);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(temporary, GSON.toJson(root), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException failure) {
            LOGGER.warn("Could not save Carbon menu preferences to {}", file, failure);
        }
    }

    private static float clamp(float value) {
        return Math.max(MIN_SCALE, Math.min(MAX_SCALE, Float.isFinite(value) ? value : 1.0f));
    }
}
