package dev.carbon.client.ui;

/** Small allocation-free easing helpers used while extracting Carbon widgets. */
public final class CarbonAnimation {
    private CarbonAnimation() {
    }

    public static float approach(float value, float target, long elapsedNanos, float responseSeconds) {
        if (elapsedNanos <= 0L) {
            return value;
        }
        double response = Math.max(0.001, responseSeconds);
        float amount = (float) (1.0 - Math.exp(-elapsedNanos / (response * 1_000_000_000.0)));
        return value + (target - value) * amount;
    }

    public static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public static float easeOutCubic(float value) {
        float t = clamp01(value);
        float inverse = 1.0f - t;
        return 1.0f - inverse * inverse * inverse;
    }

    public static float easeOutBack(float value) {
        float t = clamp01(value) - 1.0f;
        float overshoot = 1.70158f;
        return 1.0f + (overshoot + 1.0f) * t * t * t + overshoot * t * t;
    }

    public static float pulse(long timeNanos, float frequencyHz) {
        double phase = timeNanos / 1_000_000_000.0 * Math.max(0.0f, frequencyHz);
        return 0.5f + 0.5f * (float) Math.sin(phase * Math.PI * 2.0);
    }
}
