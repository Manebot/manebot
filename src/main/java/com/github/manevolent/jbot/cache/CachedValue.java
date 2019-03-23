package com.github.manevolent.jbot.cache;

import java.util.function.Supplier;

public class CachedValue<T> {
    private final long timeout;
    private final Supplier<T> supplier;
    private final Object accessLock = new Object();

    private T value = null;
    private long lastSupplied = 0L;

    public CachedValue(long timeoutMs, Supplier<T> supplier) {
        this.timeout = timeoutMs * 1_000_000L;
        this.supplier = supplier;
    }

    public void unset() {
        synchronized (accessLock) {
            value = null;
        }
    }

    public T get() {
        synchronized (accessLock) {
            long time = System.nanoTime();
            if (value == null || time - lastSupplied >= timeout) {
                value = supplier.get();
                if (value != null) lastSupplied = time;
            }

            return value;
        }
    }
}
