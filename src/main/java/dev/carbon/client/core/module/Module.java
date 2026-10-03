package dev.carbon.client.core.module;

import dev.carbon.client.core.event.EventBus;
import dev.carbon.client.core.event.EventListener;
import dev.carbon.client.core.setting.KeybindSetting;
import dev.carbon.client.core.setting.Setting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/** Base class for client features. Disabled modules own no active subscriptions. */
public abstract class Module {
    private static final Logger LOGGER = LoggerFactory.getLogger("Carbon Client");
    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_.-]+");

    private final String id;
    private final String name;
    private final String description;
    private final Category category;
    private final boolean defaultEnabled;
    private final EventBus eventBus;
    private final ArrayList<Setting<?>> mutableSettings = new ArrayList<>();
    private final List<Setting<?>> settingsView = Collections.unmodifiableList(mutableSettings);
    private final ArrayList<EventBus.Subscription<?>> subscriptions = new ArrayList<>(4);
    private final KeybindSetting toggleKey;

    private boolean enabled;
    private boolean transitionInProgress;
    private Runnable changeListener;
    private Consumer<Throwable> failureListener;

    protected Module(EventBus eventBus, String id, String name, String description,
                     Category category, boolean defaultEnabled) {
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        if (id == null || !ID_PATTERN.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid module id: " + id);
        }
        this.id = id;
        this.name = Objects.requireNonNull(name, "name");
        this.description = Objects.requireNonNull(description, "description");
        this.category = Objects.requireNonNull(category, "category");
        this.defaultEnabled = defaultEnabled;
        this.toggleKey = new KeybindSetting(
                "toggle_key", "Toggle key", "Press this key to toggle the module",
                KeybindSetting.Binding.unboundBinding());
        addSetting(toggleKey);
    }

    public final String id() {
        return id;
    }

    public final String name() {
        return name;
    }

    public final String description() {
        return description;
    }

    public final Category category() {
        return category;
    }

    public final boolean enabled() {
        return enabled;
    }

    public final boolean defaultEnabled() {
        return defaultEnabled;
    }

    public final KeybindSetting toggleKey() {
        return toggleKey;
    }

    public final List<Setting<?>> settings() {
        return settingsView;
    }

    public final EventBus eventBus() {
        return eventBus;
    }

    public final Setting<?> setting(String settingId) {
        for (int index = 0; index < mutableSettings.size(); index++) {
            Setting<?> setting = mutableSettings.get(index);
            if (setting.id().equals(settingId)) {
                return setting;
            }
        }
        return null;
    }

    protected final void addSetting(Setting<?> setting) {
        Objects.requireNonNull(setting, "setting");
        for (int index = 0; index < mutableSettings.size(); index++) {
            if (mutableSettings.get(index).id().equals(setting.id())) {
                throw new IllegalArgumentException("Duplicate setting id " + setting.id() + " in module " + id);
            }
        }
        setting.setChangeListener(ignored -> notifyChanged());
        mutableSettings.add(setting);
    }

    public final void setEnabled(boolean requested) {
        if (enabled == requested || transitionInProgress) {
            return;
        }

        transitionInProgress = true;
        if (requested) {
            enabled = true;
            try {
                onEnable();
            } catch (Throwable failure) {
                disableAfterFailure(failure);
                return;
            }
            transitionInProgress = false;
            notifyChanged();
            return;
        }

        enabled = false;
        clearSubscriptions();
        try {
            onDisable();
        } catch (Throwable failure) {
            reportFailure(failure);
        }
        transitionInProgress = false;
        notifyChanged();
    }

    public final void toggle() {
        setEnabled(!enabled);
    }

    /** Disables this module and reports a failure caught at an external hook boundary. */
    public final void fail(Throwable failure) {
        disableAfterFailure(Objects.requireNonNull(failure, "failure"));
    }

    public final void setChangeListener(Runnable listener) {
        changeListener = listener;
    }

    public final void setFailureListener(Consumer<Throwable> listener) {
        failureListener = listener;
    }

    protected final <E> void listen(Class<E> eventType, EventListener<? super E> listener) {
        if (!enabled) {
            throw new IllegalStateException("Disabled module cannot subscribe: " + id);
        }
        EventBus.Subscription<E> subscription = eventBus.subscribe(eventType, event -> {
            if (!enabled) {
                return;
            }
            try {
                listener.onEvent(event);
            } catch (Throwable failure) {
                disableAfterFailure(failure);
            }
        });
        subscriptions.add(subscription);
    }

    protected void onEnable() {
    }

    protected void onDisable() {
    }

    private void disableAfterFailure(Throwable failure) {
        if (!enabled) {
            reportFailure(failure);
            return;
        }

        enabled = false;
        clearSubscriptions();
        try {
            onDisable();
        } catch (Throwable cleanupFailure) {
            reportFailure(cleanupFailure);
        }
        transitionInProgress = false;
        notifyChanged();
        reportFailure(failure);
    }

    private void clearSubscriptions() {
        for (int index = 0; index < subscriptions.size(); index++) {
            subscriptions.get(index).close();
        }
        subscriptions.clear();
    }

    private void notifyChanged() {
        Runnable listener = changeListener;
        if (listener != null) {
            try {
                listener.run();
            } catch (Throwable failure) {
                LOGGER.error("Could not persist changed settings for module '{}'", id, failure);
            }
        }
    }

    private void reportFailure(Throwable failure) {
        Consumer<Throwable> listener = failureListener;
        if (listener != null) {
            listener.accept(failure);
        } else {
            LOGGER.error("Module '{}' failed and was disabled", id, failure);
        }
    }
}
