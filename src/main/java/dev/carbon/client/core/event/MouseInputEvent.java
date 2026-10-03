package dev.carbon.client.core.event;

/** Reusable mouse callback events for keybind state; listeners read fields during dispatch. */
public final class MouseInputEvent {
    private static final MouseInputEvent[] BUTTON_EVENTS = {
            new MouseInputEvent(0), new MouseInputEvent(1), new MouseInputEvent(2), new MouseInputEvent(3),
            new MouseInputEvent(4), new MouseInputEvent(5), new MouseInputEvent(6), new MouseInputEvent(7)
    };

    private final int button;
    private int action;
    private int modifiers;

    private MouseInputEvent(int button) {
        this.button = button;
    }

    public static MouseInputEvent prepare(int button, int action, int modifiers) {
        if (button < 0 || button >= BUTTON_EVENTS.length) {
            return null;
        }
        MouseInputEvent event = BUTTON_EVENTS[button];
        event.action = action;
        event.modifiers = modifiers;
        return event;
    }

    public int button() {
        return button;
    }

    public int action() {
        return action;
    }

    public int modifiers() {
        return modifiers;
    }
}
