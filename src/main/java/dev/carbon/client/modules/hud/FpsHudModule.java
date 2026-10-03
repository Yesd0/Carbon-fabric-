package dev.carbon.client.modules.hud;

import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.event.HudRenderEvent;
import dev.carbon.client.hud.HudModule;
import net.minecraft.client.Minecraft;

public final class FpsHudModule extends HudModule {
    private int cachedFps = Integer.MIN_VALUE;
    private String cachedText = "0 FPS";
    private int cachedWidth;

    public FpsHudModule(EventBus eventBus) {
        super(eventBus, "fps", "FPS", "Shows the current frame rate", true, 0.02, 0.04);
    }

    @Override
    protected void onHudTick() {
        int fps = Minecraft.getInstance().getFps();
        if (fps == cachedFps) {
            return;
        }
        cachedFps = fps;
        cachedText = fps + " FPS";
        cachedWidth = Minecraft.getInstance().font.width(cachedText);
    }

    @Override
    protected int width() {
        return cachedWidth;
    }

    @Override
    protected int height() {
        return Minecraft.getInstance().font.lineHeight;
    }

    @Override
    protected void render(HudRenderEvent event, int x, int y) {
        event.graphics().text(Minecraft.getInstance().font, cachedText, x, y, textColor().get(), true);
    }
}
