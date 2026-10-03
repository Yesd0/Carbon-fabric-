package dev.carbon.client.core.setting;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;

public final class NumberSetting extends Setting<Double> {
    private final double minimum;
    private final double maximum;
    private final double step;

    public NumberSetting(String id, String label, String description, double defaultValue,
                         double minimum, double maximum, double step) {
        super(id, label, description, validate(defaultValue, minimum, maximum, step));
        this.minimum = minimum;
        this.maximum = maximum;
        this.step = step;
        set(defaultValue);
    }

    public double minimum() {
        return minimum;
    }

    public double maximum() {
        return maximum;
    }

    public double step() {
        return step;
    }

    public void setFromFraction(double fraction) {
        double bounded = Math.max(0.0, Math.min(1.0, fraction));
        set(minimum + (maximum - minimum) * bounded);
    }

    @Override
    public JsonElement toJson() {
        return new JsonPrimitive(get());
    }

    @Override
    public void fromJson(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Expected numeric setting: " + id());
        }
        set(value.getAsDouble());
    }

    @Override
    public String displayValue() {
        double current = get();
        if (Math.abs(current - Math.rint(current)) < 0.000001) {
            return Long.toString(Math.round(current));
        }
        return String.format(Locale.ROOT, "%.2f", current);
    }

    @Override
    protected Double normalize(Double value) {
        double finite = Double.isFinite(value) ? value : defaultValue();
        double clamped = Math.max(minimum, Math.min(maximum, finite));
        double quantized = minimum + Math.round((clamped - minimum) / step) * step;
        return Math.max(minimum, Math.min(maximum, Math.rint(quantized * 1000.0) / 1000.0));
    }

    private static double validate(double defaultValue, double minimum, double maximum, double step) {
        if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum) {
            throw new IllegalArgumentException("Invalid number-setting range");
        }
        if (!Double.isFinite(step) || step <= 0.0) {
            throw new IllegalArgumentException("Number-setting step must be positive");
        }
        if (!Double.isFinite(defaultValue) || defaultValue < minimum || defaultValue > maximum) {
            throw new IllegalArgumentException("Number-setting default is outside its range");
        }
        return defaultValue;
    }
}
