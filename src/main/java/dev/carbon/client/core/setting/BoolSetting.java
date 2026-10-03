package dev.carbon.client.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public final class BoolSetting extends Setting<Boolean> {
    public BoolSetting(String id, String label, String description, boolean defaultValue) {
        super(id, label, description, defaultValue);
    }

    public boolean enabled() {
        return get();
    }

    public void toggle() {
        set(!get());
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Expected boolean setting: " + id());
        }
        set(value.getAsBoolean());
    }

    @Override
    public String displayValue() {
        return enabled() ? "On" : "Off";
    }

    @Override
    protected Boolean normalize(Boolean value) {
        return value;
    }
}
