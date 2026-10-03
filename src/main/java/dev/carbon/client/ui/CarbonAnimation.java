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
}
