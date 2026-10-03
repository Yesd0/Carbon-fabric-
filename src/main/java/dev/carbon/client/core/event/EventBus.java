package dev.carbon.client.core.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;

/**
 * Client-thread event bus keyed by exact event class. Posting performs no copies,
 * reflective scans, stream operations, or listener-wrapper allocation.
 */
public final class EventBus {
    private final Map<Class<?>, ArrayList<Subscription<?>>> listeners = new HashMap<>();
    private int dispatchDepth;
    private boolean compactionPending;

    public <E> Subscription<E> subscribe(Class<E> eventType, EventListener<? super E> listener) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listener, "listener");

        ArrayList<Subscription<?>> bucket = listeners.get(eventType);
        if (bucket == null) {
            bucket = new ArrayList<>(4);
            listeners.put(eventType, bucket);
        } else if (dispatchDepth == 0) {
            compact(bucket);
        }

        Subscription<E> subscription = new Subscription<>(this, eventType, listener);
        bucket.add(subscription);
        return subscription;
    }

    /** Dispatches only listeners registered for the event's exact runtime class. */
    public <E> void post(E event) {
        Objects.requireNonNull(event, "event");
        ArrayList<Subscription<?>> bucket = listeners.get(event.getClass());
        if (bucket == null || bucket.isEmpty()) {
            return;
        }

        int boundary = bucket.size();
        dispatchDepth++;
        try {
            for (int index = 0; index < boundary; index++) {
                Subscription<?> subscription = bucket.get(index);
                if (subscription.active) {
                    subscription.dispatch(event);
                }
            }
        } finally {
            dispatchDepth--;
            if (dispatchDepth == 0 && compactionPending) {
                compactAll();
            }
        }
    }

    private void unsubscribe(Subscription<?> subscription) {
        if (dispatchDepth != 0) {
            compactionPending = true;
            return;
        }
        ArrayList<Subscription<?>> bucket = listeners.get(subscription.eventType);
        if (bucket != null) {
            bucket.remove(subscription);
            if (bucket.isEmpty()) {
                listeners.remove(subscription.eventType);
            }
        }
    }

    private void compactAll() {
        compactionPending = false;
        Iterator<Map.Entry<Class<?>, ArrayList<Subscription<?>>>> entries = listeners.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<Class<?>, ArrayList<Subscription<?>>> entry = entries.next();
            compact(entry.getValue());
            if (entry.getValue().isEmpty()) {
                entries.remove();
            }
        }
    }

    private static void compact(ArrayList<Subscription<?>> bucket) {
        int writeIndex = 0;
        for (int readIndex = 0; readIndex < bucket.size(); readIndex++) {
            Subscription<?> subscription = bucket.get(readIndex);
            if (subscription.active) {
                if (writeIndex != readIndex) {
                    bucket.set(writeIndex, subscription);
                }
                writeIndex++;
            }
        }
        if (writeIndex < bucket.size()) {
            bucket.subList(writeIndex, bucket.size()).clear();
        }
    }

    public static final class Subscription<E> implements AutoCloseable {
        private final EventBus bus;
        private final Class<E> eventType;
        private EventListener<? super E> listener;
        private boolean active = true;

        private Subscription(EventBus bus, Class<E> eventType, EventListener<? super E> listener) {
            this.bus = bus;
            this.eventType = eventType;
            this.listener = listener;
        }

        public boolean active() {
            return active;
        }

        private void dispatch(Object event) {
            EventListener<? super E> current = listener;
            if (current != null) {
                current.onEvent(eventType.cast(event));
            }
        }

        @Override
        public void close() {
            if (!active) {
                return;
            }
            active = false;
            listener = null;
            bus.unsubscribe(this);
        }
    }
}
