package dev.carbon.client.core.util;

/** Maps normalized HUD coordinates to an on-screen origin while keeping the element in bounds. */
public final class HudAnchor {
    private HudAnchor() {
    }

    public static int resolve(double fraction, int screenExtent, int elementExtent) {
        int available = Math.max(0, screenExtent - elementExtent);
        double normalized = Double.isFinite(fraction) ? Math.max(0.0, Math.min(1.0, fraction)) : 0.0;
        return (int) Math.round(available * normalized);
    }
}
