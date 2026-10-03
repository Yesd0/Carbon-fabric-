package dev.carbon.client.core.module;

import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.event.KeyInputEvent;
import dev.carbon.client.core.event.MouseInputEvent;
import dev.carbon.client.core.setting.KeybindSetting;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Registry and shared press-edge keybind dispatcher for the single client jar. */
public final class ModuleManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");

    private final EventBus eventBus;
    private final ArrayList<Module> mutableModules = new ArrayList<>();
    private final List<Module> modulesView = Collections.unmodifiableList(mutableModules);
    private final Map<String, Module> byId = new HashMap<>();
    private Runnable changeListener;

    public ModuleManager(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        eventBus.subscribe(KeyInputEvent.class, this::onKeyInput);
        eventBus.subscribe(MouseInputEvent.class, this::onMouseInput);
    }

    public void register(Module module) {
        Objects.requireNonNull(module, "module");
        if (byId.containsKey(module.id())) {
            throw new IllegalArgumentException("Duplicate module id: " + module.id());
        }
        if (module.eventBus() != eventBus) {
            throw new IllegalArgumentException("Module belongs to a different event bus: " + module.id());
        }
        module.setChangeListener(this::notifyChanged);
        module.setFailureListener(failure -> LOGGER.error(
                "Module '{}' failed and was disabled", module.id(), failure));
        byId.put(module.id(), module);
        mutableModules.add(module);
    }

    public Module get(String id) {
        return byId.get(id);
    }

    public <T extends Module> T get(Class<T> type) {
        for (int index = 0; index < mutableModules.size(); index++) {
            Module module = mutableModules.get(index);
            if (type.isInstance(module)) {
                return type.cast(module);
            }
        }
        return null;
    }

    public List<Module> modules() {
        return modulesView;
    }

    public EventBus eventBus() {
        return eventBus;
    }

    public void setChangeListener(Runnable listener) {
        changeListener = listener;
    }

    private void onKeyInput(KeyInputEvent event) {
        if (event.action() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) {
            return;
        }
        int keyCode = event.keyCode();
        for (int index = 0; index < mutableModules.size(); index++) {
            Module module = mutableModules.get(index);
            KeybindSetting.Binding binding = module.toggleKey().get();
            if (!binding.mouse() && !binding.unbound() && binding.code() == keyCode) {
                module.toggle();
            }
        }
    }

    private void onMouseInput(MouseInputEvent event) {
        if (event.action() != GLFW.GLFW_PRESS || Minecraft.getInstance().screen != null) {
            return;
        }
        int button = event.button();
        for (int index = 0; index < mutableModules.size(); index++) {
            Module module = mutableModules.get(index);
            KeybindSetting.Binding binding = module.toggleKey().get();
            if (binding.mouse() && !binding.unbound() && binding.code() == button) {
                module.toggle();
            }
        }
    }

    private void notifyChanged() {
        Runnable listener = changeListener;
        if (listener != null) {
            listener.run();
        }
    }
}
