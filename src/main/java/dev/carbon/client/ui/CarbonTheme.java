package dev.carbon.client.ui;

/** Shared translucent Carbon glass palette. Colors use ARGB. */
public final class CarbonTheme {
    public static final int SCRIM = 0x43050A07;
    public static final int FRAME = 0xCF0D1511;
    public static final int PANEL = 0x85141D18;
    public static final int PANEL_RAISED = 0xA3202C25;
    public static final int PANEL_HOVER = 0xB52B3D32;
    public static final int CARD = 0x79131C17;
    public static final int CARD_HOVER = 0x9D1C2B22;
    public static final int ACCENT = 0xFF55E695;
    public static final int ACCENT_DEEP = 0xFF1D8D55;
    public static final int ACCENT_MUTED = 0xFF285A3C;
    public static final int BORDER = 0x8855E695;
    public static final int BORDER_SOFT = 0x557D9C86;
    public static final int TEXT = 0xFFF1F7F2;
    public static final int TEXT_MUTED = 0xFFA4B6AA;
    public static final int TEXT_DIM = 0xFF708176;
    public static final int TRACK_OFF = 0xFF35443A;
    public static final int SUCCESS = 0xFF58D895;
    public static final int SUCCESS_SURFACE = 0xCB187A4A;
    public static final int WARNING = 0xFFFFD27A;
    public static final int ERROR = 0xFFFF8585;
    public static final int ERROR_SURFACE = 0xC44B2030;

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
