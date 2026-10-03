package dev.carbon.client.core.util;

public final class ColorUtil {
    private ColorUtil() {
    }

    public static int withAlpha(int argb, int alpha) {
        return (argb & 0x00FFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    public static int blend(int firstArgb, int secondArgb, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int a = interpolate(firstArgb >>> 24, secondArgb >>> 24, t);
        int r = interpolate((firstArgb >>> 16) & 255, (secondArgb >>> 16) & 255, t);
        int g = interpolate((firstArgb >>> 8) & 255, (secondArgb >>> 8) & 255, t);
        int b = interpolate(firstArgb & 255, secondArgb & 255, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int interpolate(int start, int end, float amount) {
        return Math.round(start + (end - start) * amount);
    }
}
