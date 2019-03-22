package com.github.manevolent.jbot.command;

import com.github.manevolent.jbot.command.executor.CommandExecutor;

import java.util.*;

public final class DefaultCommandManager extends CommandManager {
    private final Map<String, Registration> registrations = new LinkedHashMap<>();
    private final Object registrationLock = new Object();

    @Override
    public Registration registerExecutor(String label, CommandExecutor executor) {
        synchronized (registrationLock) {
            if (registrations.containsKey(label))
                throw new IllegalArgumentException("command " + label + " already exists.");
            Registration registration = new Registration(executor, label);
            registrations.put(label, registration);
            return registration;
        }
    }

    @Override
    public void unregisterExecutor(String label) {
        synchronized (registrationLock) {
            registrations.remove(label);
        }
    }

    @Override
    public CommandExecutor getExecutor(String label) {
        Registration registration = registrations.get(label);
        if (registration == null) return null;
        return registration.getExecutor();
    }

    @Override
    public Collection<Registration> getRegistrations() {
        return Collections.unmodifiableCollection(registrations.values());
    }
}
