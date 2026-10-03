package dev.carbon.client.ui;

import dev.carbon.client.core.config.ConfigManager;
import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.event.KeyInputEvent;
import dev.carbon.client.core.event.MouseInputEvent;
import dev.carbon.client.core.module.ModuleManager;
import dev.carbon.client.ui.render.CarbonGlass;
import dev.carbon.client.ui.render.CarbonRenderPipelines;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers Carbon's rendering hooks and the Part A visual-test keybind. */
public final class CarbonUI {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");

    private static ModuleManager moduleManager;
    private static ConfigManager configManager;
    private static boolean initialized;

    private CarbonUI() {
    }

    public static synchronized void initialize(EventBus eventBus, ModuleManager modules, ConfigManager config) {
        if (initialized) {
            return;
        }

        moduleManager = modules;
        configManager = config;
        try {
            CarbonRenderPipelines.register();
        } catch (Throwable failure) {
            CarbonGlass.reportFailure("SDF glass pipeline registration failed", failure);
            LOGGER.error("Carbon SDF pipeline registration failed; no flat fallback will be used", failure);
        }

        eventBus.subscribe(KeyInputEvent.class, CarbonUI::onKeyInput);
        eventBus.subscribe(MouseInputEvent.class, CarbonUI::onMouseInput);
        initialized = true;
    }

    private static void onKeyInput(KeyInputEvent event) {
        if (moduleManager == null) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() instanceof CarbonDemoScreen demoScreen && demoScreen.captureKey(event)) {
            return;
        }
        if (event.keyCode() == GLFW.GLFW_KEY_F8 && event.action() == GLFW.GLFW_PRESS && client.gui.screen() == null) {
            client.gui.setScreen(new CarbonGlassTestScreen(null));
        }
    }

    private static void onMouseInput(MouseInputEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client.gui.screen() instanceof CarbonDemoScreen demoScreen) {
            demoScreen.captureMouse(event);
        }
    }
}
