package dev.carbon.client.ui;

/** Carbon's matte dark surface, green-accent, border, shadow, and text tokens (ARGB). */
public final class CarbonTheme {
    public static final boolean NATIVE_BACKGROUND_BLUR_DEFAULT = false;
    public static final float WINDOW_OPACITY = 0.96f;
    public static final int BACKDROP = 0x73000000;          // 45% black
    public static final int VIGNETTE = 0x55000000;

    public static final int WINDOW_TOP = 0xF516191C;        // 96% surface opacity
    public static final int WINDOW_BOTTOM = 0xF50D0F12;
    public static final int WINDOW_BORDER = 0x1AFFFFFF;     // 10% white
    public static final int WINDOW_HIGHLIGHT = 0x12FFFFFF;
    public static final int WINDOW_SHADOW = 0x8C000000;     // 55% black
    public static final int HEADER_TOP = 0xF7191C1F;
    public static final int HEADER_BOTTOM = 0xF4131619;
    public static final int SIDEBAR_TOP = 0xF414171A;
    public static final int SIDEBAR_BOTTOM = 0xF30F1215;
    public static final int CONTENT_TOP = 0xF30F1215;
    public static final int CONTENT_BOTTOM = 0xF20C0E11;

    public static final int CARD_TOP = 0xF51B1E21;
    public static final int CARD_BOTTOM = 0xF5101316;
    public static final int CARD_HOVER_TOP = 0xF5222528;
    public static final int CARD_HOVER_BOTTOM = 0xF516191C;
    public static final int CARD_BORDER = 0x24FFFFFF;
    public static final int CARD_ACTIVE_BORDER = 0x731FC76F;
    public static final int CARD_ACTIVE_WASH_TOP = 0x281FC76F;
    public static final int CARD_ACTIVE_WASH_BOTTOM = 0x0F1FC76F;

    public static final int BUTTON_TOP = 0xFF2A2E31;
    public static final int BUTTON_BOTTOM = 0xFF202427;
    public static final int BUTTON_HOVER_TOP = 0xFF34393D;
    public static final int BUTTON_HOVER_BOTTOM = 0xFF272C30;
    public static final int BUTTON_BORDER = 0x22FFFFFF;
    public static final int INPUT_TOP = 0xF90D1012;
    public static final int INPUT_BOTTOM = 0xF50A0C0E;
    public static final int INPUT_BORDER = 0x26FFFFFF;

    public static final int ACCENT = 0xFF1FC76F;
    public static final int ACCENT_DEEP = 0xFF13A95B;
    public static final int ACCENT_HIGHLIGHT = 0xFF39E18E;
    public static final int ACCENT_GLOW = 0x381FC76F;
    public static final int ACCENT_SOFT = 0x251FC76F;

    public static final int TEXT = 0xEBF4F6F3;
    public static final int TEXT_MUTED = 0xB3CDD1CE;
    public static final int TEXT_DIM = 0x80AEB4B0;
    public static final int TEXT_DISABLED = 0x5C9AA19D;
    public static final int RED = 0xFFFF5968;
    public static final int RED_SOFT = 0x24FF5968;

    public static final int DIVIDER = 0x1FFFFFFF;
    public static final int TRACK_OFF = 0xFF303438;
    public static final int TRACK_ON = ACCENT;
    public static final int TRACK_ON_BOTTOM = ACCENT_DEEP;

    public enum DisabledBarStyle {
        NEUTRAL,
        RED
    }

    private CarbonTheme() {
    }

    public static int disabledBarTop(DisabledBarStyle style) {
        return style == DisabledBarStyle.RED ? 0xFFB52B40 : 0xFF303438;
    }

    public static int disabledBarBottom(DisabledBarStyle style) {
        return style == DisabledBarStyle.RED ? 0xFF871B2D : 0xFF24282C;
    }

    public static int mix(int from, int to, float amount) {
        float t = clamp01(amount);
        int a = interpolate(from >>> 24, to >>> 24, t);
        int r = interpolate((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = interpolate((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = interpolate(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int alphaScale(int color, float factor) {
        int alpha = interpolate(color >>> 24, 0, 1.0f - clamp01(factor));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    private static int interpolate(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount);
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }
}
