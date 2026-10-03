package dev.carbon.client.modules.visual;

import dev.carbon.client.core.event.ClientTickEvent;
import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.event.KeyInputEvent;
import dev.carbon.client.core.event.MouseInputEvent;
import dev.carbon.client.core.module.Category;
import dev.carbon.client.core.module.Module;
import dev.carbon.client.core.setting.KeybindSetting;
import dev.carbon.client.core.setting.NumberSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public final class ZoomModule extends Module {
    private static volatile ZoomModule instance;

    private final KeybindSetting holdKey = new KeybindSetting(
            "zoom_key", "Zoom key", "Hold this key to zoom", KeybindSetting.Binding.keyboard(GLFW.GLFW_KEY_C));
    private final NumberSetting magnification = new NumberSetting(
            "magnification", "Magnification", "Maximum zoom multiplier", 4.0, 2.0, 12.0, 0.5);
    private volatile boolean keyHeld;

    public ZoomModule(EventBus eventBus) {
        super(eventBus, "zoom", "Zoom", "Hold a key for a field-of-view zoom", Category.VISUAL, true);
        addSetting(holdKey);
        addSetting(magnification);
        instance = this;
    }

    @Override
    protected void onEnable() {
        listen(ClientTickEvent.class, this::onTick);
        listen(KeyInputEvent.class, this::onKeyInput);
        listen(MouseInputEvent.class, this::onMouseInput);
    }

    @Override
    protected void onDisable() {
        keyHeld = false;
    }

    private void onTick(ClientTickEvent event) {
        if (event.phase() == ClientTickEvent.Phase.END && Minecraft.getInstance().screen != null) {
            keyHeld = false;
        }
    }

    private void onKeyInput(KeyInputEvent event) {
        if (Minecraft.getInstance().screen != null) {
            keyHeld = false;
            return;
        }
        KeybindSetting.Binding binding = holdKey.get();
        if (binding.unbound() || binding.mouse() || binding.code() != event.keyCode()) {
            return;
        }
        if (event.action() == GLFW.GLFW_PRESS) {
            keyHeld = true;
        } else if (event.action() == GLFW.GLFW_RELEASE) {
            keyHeld = false;
        }
    }

    private void onMouseInput(MouseInputEvent event) {
        if (Minecraft.getInstance().screen != null) {
            keyHeld = false;
            return;
        }
        KeybindSetting.Binding binding = holdKey.get();
        if (binding.unbound() || !binding.mouse() || binding.code() != event.button()) {
            return;
        }
        if (event.action() == GLFW.GLFW_PRESS) {
            keyHeld = true;
        } else if (event.action() == GLFW.GLFW_RELEASE) {
            keyHeld = false;
        }
    }

    /** Called from the camera FOV hook. A disabled or idle zoom is a no-op. */
    public static float applyFov(float vanillaFov) {
        ZoomModule module = instance;
        if (module == null || !module.enabled() || !module.keyHeld) {
            return vanillaFov;
        }
        try {
            double zoom = module.magnification.get();
            if (!Double.isFinite(zoom) || zoom <= 1.0) {
                return vanillaFov;
            }
            return (float) (vanillaFov / zoom);
        } catch (Throwable failure) {
            module.fail(failure);
            return vanillaFov;
        }
    }
}
