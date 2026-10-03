package dev.carbon.client;

import dev.carbon.client.core.event.ClientTickEvent;
import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.event.KeyInputEvent;
import dev.carbon.client.core.event.MouseClickEvent;
import dev.carbon.client.core.event.MouseInputEvent;
import dev.carbon.client.core.module.ModuleManager;
import dev.carbon.client.core.config.ConfigManager;
import dev.carbon.client.hud.HudManager;
import dev.carbon.client.ui.CarbonUI;
import dev.carbon.client.modules.hud.CpsHudModule;
import dev.carbon.client.modules.hud.FpsHudModule;
import dev.carbon.client.modules.hud.KeystrokesHudModule;
import dev.carbon.client.modules.visual.ZoomModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CarbonClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");

    private static volatile CarbonClient instance;

    private final EventBus eventBus = new EventBus();
    private ModuleManager moduleManager;
    private ConfigManager configManager;
    private HudManager hudManager;

    @Override
    public void onInitializeClient() {
        instance = this;
        try {
            moduleManager = new ModuleManager(eventBus);
            moduleManager.register(new KeystrokesHudModule(eventBus));
            moduleManager.register(new CpsHudModule(eventBus));
            moduleManager.register(new FpsHudModule(eventBus));
            moduleManager.register(new ZoomModule(eventBus));

            configManager = new ConfigManager(
                    FabricLoader.getInstance().getConfigDir().resolve("carbonclient"), moduleManager);
            configManager.load();

            try {
                CarbonUI.initialize(eventBus, moduleManager);
            } catch (Throwable failure) {
                LOGGER.error("Could not initialize Carbon UI; the client modules remain available", failure);
            }

            hudManager = new HudManager(eventBus);
            try {
                hudManager.register();
            } catch (Throwable failure) {
                LOGGER.error("Could not register Carbon Client HUD rendering", failure);
            }

            ClientTickEvents.END_CLIENT_TICK.register(this::onClientTick);
            ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
                if (configManager != null) {
                    configManager.close();
                }
            });
            LOGGER.info("Initialized Carbon Client with {} modules", moduleManager.modules().size());
        } catch (Throwable failure) {
            LOGGER.error("Carbon Client initialization failed; the client will continue without it", failure);
        }
    }

    public static void onKeyInput(int keyCode, int scanCode, int action, int modifiers) {
        CarbonClient client = instance;
        if (client == null || client.moduleManager == null) {
            return;
        }
        KeyInputEvent event = KeyInputEvent.INSTANCE;
        event.prepare(keyCode, scanCode, action, modifiers);
        client.eventBus.post(event);
    }

    public static void onMouseInput(int button, int action, int modifiers) {
        CarbonClient client = instance;
        if (client == null || client.moduleManager == null) {
            return;
        }
        MouseInputEvent event = MouseInputEvent.prepare(button, action, modifiers);
        if (event != null) {
            client.eventBus.post(event);
        }
    }

    public static void onMouseButton(int button) {
        CarbonClient client = instance;
        if (client == null || client.moduleManager == null) {
            return;
        }
        net.minecraft.client.Minecraft minecraft = net.minecraft.client.Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        MouseClickEvent event;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            event = MouseClickEvent.LEFT;
        } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            event = MouseClickEvent.RIGHT;
        } else {
            return;
        }
        event.prepare(System.nanoTime());
        client.eventBus.post(event);
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public ModuleManager moduleManager() {
        return moduleManager;
    }

    public ConfigManager configManager() {
        return configManager;
    }

    private void onClientTick(net.minecraft.client.Minecraft client) {
        eventBus.post(ClientTickEvent.END);
    }
}
