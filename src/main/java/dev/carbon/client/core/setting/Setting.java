package dev.carbon.client.core.setting;

import com.google.gson.JsonElement;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Base for a stable-ID, typed, serializable setting. */
public abstract class Setting<T> {
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]+");

    private final String id;
    private final String label;
    private final String description;
    private final T defaultValue;
    private T value;
    private Consumer<Setting<?>> changeListener;

    protected Setting(String id, String label, String description, T defaultValue) {
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid setting id: " + id);
        }
        this.id = id;
        this.label = Objects.requireNonNull(label, "label");
        this.description = Objects.requireNonNull(description, "description");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        // Do not dispatch to a subclass normalizer before subclass fields are initialized.
        this.value = defaultValue;
    }

    public final String id() {
        return id;
    }

    public final String label() {
        return label;
    }

    public final String description() {
        return description;
    }

    public final T get() {
        return value;
    }

    public final T defaultValue() {
        return defaultValue;
    }

    public final void setChangeListener(Consumer<Setting<?>> listener) {
        changeListener = listener;
    }

    public final boolean set(T candidate) {
        T next = normalize(Objects.requireNonNull(candidate, "candidate"));
        if (Objects.equals(value, next)) {
            return false;
        }
        value = next;
        Consumer<Setting<?>> listener = changeListener;
        if (listener != null) {
            listener.accept(this);
        }
        return true;
    }

    public final void reset() {
        set(defaultValue);
    }

    public abstract JsonElement toJson();

    public abstract void fromJson(JsonElement value);

    public String displayValue() {
        return String.valueOf(value);
    }

    protected abstract T normalize(T value);
}
