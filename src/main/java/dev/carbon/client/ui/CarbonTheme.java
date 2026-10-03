package dev.carbon.client.ui;

/** Shared Carbon green palette for the client interface. Colors use ARGB. */
public final class CarbonTheme {
    public static final int SCRIM = 0xA807100B;
    public static final int FRAME = 0xF20D1511;
    public static final int PANEL = 0xF31A241E;
    public static final int PANEL_RAISED = 0xF6233028;
    public static final int PANEL_HOVER = 0xF92A3A30;
    public static final int ACCENT = 0xFF55E695;
    public static final int ACCENT_DEEP = 0xFF1D8D55;
    public static final int ACCENT_MUTED = 0xFF285A3C;
    public static final int BORDER = 0x805A8A6A;
    public static final int TEXT = 0xFFF1F7F2;
    public static final int TEXT_MUTED = 0xFFA4B6AA;
    public static final int TEXT_DIM = 0xFF708176;
    public static final int TRACK_OFF = 0xFF35443A;
    public static final int SUCCESS = 0xFF64E99E;
    public static final int WARNING = 0xFFFFD27A;
    public static final int ERROR = 0xFFFF8585;

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
