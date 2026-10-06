package dev.carbon.client.hud;

import dev.carbon.client.core.event.ClientTickEvent;
import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.event.HudRenderEvent;
import dev.carbon.client.core.module.Category;
import dev.carbon.client.core.module.Module;
import dev.carbon.client.core.setting.ColorSetting;
import dev.carbon.client.core.setting.NumberSetting;
import dev.carbon.client.core.util.HudAnchor;

/** Shared event lifecycle, normalized placement, and persistent sizing for vanilla HUD features. */
public abstract class HudModule extends Module {
    private final NumberSetting xPosition;
    private final NumberSetting yPosition;
    private final NumberSetting scale;
    private final ColorSetting textColor;

    protected HudModule(EventBus eventBus, String id, String name, String description,
                        boolean defaultEnabled, double defaultX, double defaultY) {
        super(eventBus, id, name, description, Category.HUD, defaultEnabled);
        xPosition = new NumberSetting(
                "x", "Horizontal position", "Position from the left edge", defaultX, 0.0, 1.0, 0.001);
        yPosition = new NumberSetting(
                "y", "Vertical position", "Position from the top edge", defaultY, 0.0, 1.0, 0.001);
        scale = new NumberSetting(
                "scale", "HUD scale", "Size of this HUD element", 1.0, 0.5, 2.0, 0.05);
        textColor = new ColorSetting(
                "text_color", "Text color", "HUD label color", 0xFFFFFFFF);
        addSetting(xPosition);
        addSetting(yPosition);
        addSetting(scale);
        addSetting(textColor);
    }

    public final NumberSetting xPosition() {
        return xPosition;
    }

    public final NumberSetting yPosition() {
        return yPosition;
    }

    public final NumberSetting scaleSetting() {
        return scale;
    }

    public final float hudScale() {
        return scale.get().floatValue();
    }

    public final ColorSetting textColor() {
        return textColor;
    }

    /** Current on-screen bounds used by both rendering and the layout editor. */
    public final int renderedWidth() {
        return Math.max(1, Math.round(Math.max(1, width()) * hudScale()));
    }

    public final int renderedHeight() {
        return Math.max(1, Math.round(Math.max(1, height()) * hudScale()));
    }

    public final int screenX(int screenWidth) {
        return HudAnchor.resolve(xPosition.get(), screenWidth, renderedWidth());
    }

    public final int screenY(int screenHeight) {
        return HudAnchor.resolve(yPosition.get(), screenHeight, renderedHeight());
    }

    /** Stores pixel coordinates in the existing normalized settings so profiles keep the layout. */
    public final void setEditorPosition(int x, int y, int screenWidth, int screenHeight) {
        int availableX = Math.max(0, screenWidth - renderedWidth());
        int availableY = Math.max(0, screenHeight - renderedHeight());
        int clampedX = Math.max(0, Math.min(availableX, x));
        int clampedY = Math.max(0, Math.min(availableY, y));
        xPosition.set(availableX == 0 ? 0.0 : (double) clampedX / availableX);
        yPosition.set(availableY == 0 ? 0.0 : (double) clampedY / availableY);
    }

    public final void setEditorScale(double value) {
        scale.set(value);
    }

    /** Resets only placement and size, preserving enabled state and all other module settings. */
    public final void resetLayout() {
        xPosition.reset();
        yPosition.reset();
        scale.reset();
    }

    /** Disabled modules still get a live-size preview while the HUD editor is open. */
    public final void refreshEditorPreview() {
        if (!enabled()) {
            onHudTick();
        }
    }

    /** Draws an editor preview at its saved position without changing enabled state or subscriptions. */
    public final void extractEditorPreview(HudRenderEvent event) {
        renderAt(event, screenX(event.width()), screenY(event.height()));
    }

    @Override
    protected final void onEnable() {
        listen(ClientTickEvent.class, this::handleTick);
        listen(HudRenderEvent.class, this::handleRender);
        onHudEnable();
    }

    @Override
    protected final void onDisable() {
        onHudDisable();
    }

    protected void onHudEnable() {
    }

    protected void onHudDisable() {
    }

    protected void onHudTick() {
    }

    protected abstract int width();

    protected abstract int height();

    protected abstract void render(HudRenderEvent event, int x, int y);

    private void handleTick(ClientTickEvent event) {
        if (event.phase() == ClientTickEvent.Phase.END) {
            onHudTick();
        }
    }

    private void handleRender(HudRenderEvent event) {
        renderAt(event, screenX(event.width()), screenY(event.height()));
    }

    private void renderAt(HudRenderEvent event, int x, int y) {
        float factor = hudScale();
        event.graphics().pose().pushMatrix();
        try {
            event.graphics().pose().translate(x, y);
            event.graphics().pose().scale(factor, factor);
            render(event, 0, 0);
        } finally {
            event.graphics().pose().popMatrix();
        }
    }
}
