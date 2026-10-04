package dev.carbon.client.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.carbon.client.core.event.ClientTickEvent;
import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.config.ConfigManager;
import dev.carbon.client.core.module.ModuleManager;
import dev.carbon.client.ui.render.CarbonShapes;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Registers Carbon's menu in vanilla Controls and handles its key mapping on client ticks. */
public final class CarbonUI {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final Identifier CATEGORY_ID = Identifier.fromNamespaceAndPath("carbonclient", "controls");

    private static ModuleManager moduleManager;
    private static ConfigManager configManager;
    private static KeyMapping openMenuKey;
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
            CarbonShapes.initialize();
        } catch (Throwable failure) {
            LOGGER.error("Carbon smooth-shape pipeline registration failed; the menu will use native fills", failure);
        }
        KeyMapping.Category category = KeyMapping.Category.register(CATEGORY_ID);
        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.carbonclient.open_menu",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category));
        eventBus.subscribe(ClientTickEvent.class, CarbonUI::onClientTick);
        initialized = true;
        LOGGER.info("Registered Carbon menu key in Minecraft Controls (default: Right Shift)");
    }

    /** Localized display label used by the in-menu controls page. */
    public static String menuKeyLabel() {
        return openMenuKey == null ? "Right Shift" : openMenuKey.getTranslatedKeyMessage().getString();
    }

    private static void onClientTick(ClientTickEvent event) {
        if (event.phase() != ClientTickEvent.Phase.END || openMenuKey == null || moduleManager == null) {
            return;
        }
        Minecraft client = Minecraft.getInstance();
        while (openMenuKey.consumeClick()) {
            if (client.gui.screen() == null) {
                client.gui.setScreen(new CarbonMenuScreen(moduleManager, configManager, null));
            }
        }
    }
}
