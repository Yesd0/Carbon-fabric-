package dev.carbon.client.hud;

import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.event.HudRenderEvent;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.util.Objects;

/** Connects Fabric's HUD extraction layer to the reusable internal render event. */
public final class HudManager {
    private static final Identifier HUD_LAYER = Identifier.fromNamespaceAndPath("carbonclient", "hud");

    private final EventBus eventBus;
    private final HudRenderEvent renderEvent = new HudRenderEvent();
    private boolean registered;
    private boolean editorPreviewActive;

    public HudManager(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public void register() {
        if (registered) {
            return;
        }
        HudElementRegistry.addLast(HUD_LAYER, this::extract);
        registered = true;
    }

    public void setEditorPreviewActive(boolean active) {
        editorPreviewActive = active;
    }

    public boolean editorPreviewActive() {
        return editorPreviewActive;
    }

    private void extract(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        if (editorPreviewActive) {
            return;
        }
        renderEvent.prepare(graphics, deltaTracker);
        eventBus.post(renderEvent);
    }
}
