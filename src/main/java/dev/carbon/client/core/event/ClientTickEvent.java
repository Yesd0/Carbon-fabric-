package dev.carbon.client.core.event;

/** Reused tick markers; Fabric callbacks publish these constants without allocating. */
public final class ClientTickEvent {
    public static final ClientTickEvent START = new ClientTickEvent(Phase.START);
    public static final ClientTickEvent END = new ClientTickEvent(Phase.END);

    private final Phase phase;

    private ClientTickEvent(Phase phase) {
        this.phase = phase;
    }

    public Phase phase() {
        return phase;
    }

    public enum Phase {
        START,
        END
    }
}
