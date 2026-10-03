package dev.carbon.client.core.event;

/** Reusable primary-button click events; callbacks must read the timestamp during dispatch. */
public final class MouseClickEvent {
    public static final MouseClickEvent LEFT = new MouseClickEvent(Button.LEFT);
    public static final MouseClickEvent RIGHT = new MouseClickEvent(Button.RIGHT);

    private final Button button;
    private long timestampNanos;

    private MouseClickEvent(Button button) {
        this.button = button;
    }

    public Button button() {
        return button;
    }

    public long timestampNanos() {
        return timestampNanos;
    }

    public void prepare(long timestampNanos) {
        this.timestampNanos = timestampNanos;
    }

    public enum Button {
        LEFT,
        RIGHT
    }
}
