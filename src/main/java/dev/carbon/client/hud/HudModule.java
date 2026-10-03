package dev.carbon.client.hud;

import dev.carbon.client.core.event.ClientTickEvent;
import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.event.HudRenderEvent;
import dev.carbon.client.core.module.Category;
import dev.carbon.client.core.module.Module;
import dev.carbon.client.core.setting.ColorSetting;
import dev.carbon.client.core.setting.NumberSetting;
import dev.carbon.client.core.util.HudAnchor;

/** Shared event lifecycle and normalized placement for vanilla-rendered HUD features. */
public abstract class HudModule extends Module {
    private final NumberSetting xPosition;
    private final NumberSetting yPosition;
    private final ColorSetting textColor;

    protected HudModule(EventBus eventBus, String id, String name, String description,
                        boolean defaultEnabled, double defaultX, double defaultY) {
        super(eventBus, id, name, description, Category.HUD, defaultEnabled);
        xPosition = new NumberSetting(
                "x", "Horizontal position", "Position from the left edge", defaultX, 0.0, 1.0, 0.01);
        yPosition = new NumberSetting(
                "y", "Vertical position", "Position from the top edge", defaultY, 0.0, 1.0, 0.01);
        textColor = new ColorSetting(
                "text_color", "Text color", "HUD label color", 0xFFFFFFFF);
        addSetting(xPosition);
        addSetting(yPosition);
        addSetting(textColor);
    }

    public final NumberSetting xPosition() {
        return xPosition;
    }

    public final NumberSetting yPosition() {
        return yPosition;
    }

    public final ColorSetting textColor() {
        return textColor;
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
        int x = HudAnchor.resolve(xPosition.get(), event.width(), width());
        int y = HudAnchor.resolve(yPosition.get(), event.height(), height());
        render(event, x, y);
    }
}
