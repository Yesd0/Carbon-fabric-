package dev.carbon.client.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.List;

public final class ModeSetting extends Setting<String> {
    private final List<String> modes;

    public ModeSetting(String id, String label, String description, String defaultValue, List<String> modes) {
        super(id, label, description, defaultValue);
        if (modes == null || modes.isEmpty() || !modes.contains(defaultValue)) {
            throw new IllegalArgumentException("Mode list must contain its default");
        }
        this.modes = List.copyOf(modes);
        set(defaultValue);
    }

    public List<String> modes() {
        return modes;
    }

    public void cycle() {
        int index = modes.indexOf(get());
        set(modes.get((index + 1) % modes.size()));
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Expected mode setting: " + id());
        }
        set(value.getAsString());
    }

    @Override
    protected String normalize(String value) {
        if (modes != null && !modes.isEmpty() && !modes.contains(value)) {
            throw new IllegalArgumentException("Unsupported mode for setting " + id());
        }
        return value;
    }
}
