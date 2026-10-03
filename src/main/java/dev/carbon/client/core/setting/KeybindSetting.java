package dev.carbon.client.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

public final class KeybindSetting extends Setting<KeybindSetting.Binding> {
    public KeybindSetting(String id, String label, String description, Binding defaultValue) {
        super(id, label, description, defaultValue);
    }

    @Override
    public JsonElement toJson() {
        JsonObject object = new JsonObject();
        object.addProperty("mouse", get().mouse());
        object.addProperty("code", get().code());
        return object;
    }

    @Override
    public void fromJson(JsonElement value) {
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("Expected keybind object: " + id());
        }
        JsonObject object = value.getAsJsonObject();
        if (!object.has("mouse") || !object.has("code")) {
            throw new IllegalArgumentException("Incomplete keybind: " + id());
        }
        set(new Binding(object.get("mouse").getAsBoolean(), object.get("code").getAsInt()));
    }

    @Override
    public String displayValue() {
        Binding binding = get();
        if (binding.unbound()) {
            return "Unbound";
        }
        return (binding.mouse() ? "Mouse " : "Key ") + binding.code();
    }

    @Override
    protected Binding normalize(Binding value) {
        return value.unbound() ? Binding.unboundBinding() : value;
    }

    public record Binding(boolean mouse, int code) {
        public static Binding unboundBinding() {
            return new Binding(false, -1);
        }

        public static Binding keyboard(int keyCode) {
            return new Binding(false, keyCode);
        }

        public static Binding mouseButton(int button) {
            return new Binding(true, button);
        }

        public boolean unbound() {
            return code < 0;
        }
    }
}
