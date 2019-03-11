package com.github.manevolent.jbot.event;

import java.lang.reflect.Method;

/**
 * Executes an event using Java reflection.
 */
public class DefaultEventExecutor implements EventExecutor {
    private Method method;

    private EventListener eventListener;
    private EventPriority eventPriority;

    public DefaultEventExecutor(
            EventListener eventListener,
            EventPriority eventPriority,
            Method method) {
        this.eventListener = eventListener;
        this.eventPriority = eventPriority;
        this.method = method;
    }

    @Override
    public void fire(Event event) throws EventExecutionException {
        try {
            method.invoke(eventListener, event);
        } catch (Exception e) {
            throw new EventExecutionException(e);
        }
    }

    @Override
    public EventListener getListener() {
        return eventListener;
    }

    @Override
    public EventPriority getPriority() {
        return eventPriority;
    }
}