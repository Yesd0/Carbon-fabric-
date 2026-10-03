package dev.carbon.client.core.event;

@FunctionalInterface
public interface EventListener<E> {
    void onEvent(E event);
}
