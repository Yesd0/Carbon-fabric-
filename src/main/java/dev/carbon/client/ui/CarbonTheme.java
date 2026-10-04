package dev.carbon.client.ui;

/** Opaque grayscale palette shared by Carbon's compact module menu. Colors use ARGB. */
public final class CarbonTheme {
    public static final int FRAME = 0xFF0D0F12;
    public static final int SIDEBAR = 0xFF101215;
    public static final int PANEL = 0xFF141619;
    public static final int PANEL_RAISED = 0xFF1A1D21;
    public static final int PANEL_HOVER = 0xFF22252A;
    public static final int CARD = 0xFF191C20;
    public static final int CARD_HOVER = 0xFF22252A;
    public static final int ACCENT = 0xFFF5F5F6;
    public static final int ACCENT_DEEP = 0xFFD8D9DC;
    public static final int ACCENT_MUTED = 0xFF777A80;
    public static final int BORDER = 0xFF45484E;
    public static final int BORDER_SOFT = 0xFF2C2F34;
    public static final int TEXT = 0xFFF2F2F3;
    public static final int TEXT_MUTED = 0xFFA9ABB0;
    public static final int TEXT_DIM = 0xFF73767C;
    public static final int TRACK_OFF = 0xFF303338;
    public static final int SUCCESS = 0xFFE8E9EB;
    public static final int WARNING = 0xFFD4D5D8;
    public static final int ERROR = 0xFFC6C7CA;

    private CarbonTheme() {
    }

    public static int mix(int from, int to, float amount) {
        float t = Math.max(0.0f, Math.min(1.0f, amount));
        int a = interpolate(from >>> 24, to >>> 24, t);
        int r = interpolate((from >>> 16) & 0xFF, (to >>> 16) & 0xFF, t);
        int g = interpolate((from >>> 8) & 0xFF, (to >>> 8) & 0xFF, t);
        int b = interpolate(from & 0xFF, to & 0xFF, t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int interpolate(int from, int to, float amount) {
        return Math.round(from + (to - from) * amount);
    }
}
