package dev.carbon.client.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public final class ColorSetting extends Setting<Integer> {
    public ColorSetting(String id, String label, String description, int defaultArgb) {
        super(id, label, description, defaultArgb);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(String.format(Locale.ROOT, "#%08X", get()));
    }

    @Override
    public void fromJson(JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) {
            throw new IllegalArgumentException("Expected ARGB color setting: " + id());
        }
        if (value.getAsJsonPrimitive().isNumber()) {
            set(value.getAsInt());
            return;
        }
        String hex = value.getAsString();
        if (hex.startsWith("#")) {
            hex = hex.substring(1);
        }
        long parsed = Long.parseLong(hex, 16);
        if (hex.length() == 6) {
            parsed |= 0xFF000000L;
        }
        set((int) parsed);
    }

    @Override
    public String displayValue() {
        return String.format(Locale.ROOT, "#%08X", get());
    }

    @Override
    protected Integer normalize(Integer value) {
        return value;
    }
}
