package dev.carbon.client.core.event;

/** Reused keyboard callback event. Listeners must read its fields during dispatch. */
public final class KeyInputEvent {
    public static final KeyInputEvent INSTANCE = new KeyInputEvent();

    private int keyCode;
    private int scanCode;
    private int action;
    private int modifiers;

    private KeyInputEvent() {
    }

    public void prepare(int keyCode, int scanCode, int action, int modifiers) {
        this.keyCode = keyCode;
        this.scanCode = scanCode;
        this.action = action;
        this.modifiers = modifiers;
    }

    public int keyCode() {
        return keyCode;
    }

    public int scanCode() {
        return scanCode;
    }

    public int action() {
        return action;
    }

    public int modifiers() {
        return modifiers;
    }
}
