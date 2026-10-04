package dev.carbon.client.core.event;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Mutable render-context event reused by HudManager so each frame needs no event allocation. */
public final class HudRenderEvent {
    private GuiGraphicsExtractor graphics;
    private DeltaTracker deltaTracker;
    private int width;
    private int height;

    public HudRenderEvent() {
    }

    public void prepare(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        this.graphics = graphics;
        this.deltaTracker = deltaTracker;
        this.width = graphics.guiWidth();
        this.height = graphics.guiHeight();
    }

    /** Prepares the same render context for editor previews that are extracted by a Screen. */
    public void prepareForEditor(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
        this.deltaTracker = null;
        this.width = graphics.guiWidth();
        this.height = graphics.guiHeight();
    }

    public GuiGraphicsExtractor graphics() {
        return graphics;
    }

    public DeltaTracker deltaTracker() {
        return deltaTracker;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }
}
