package com.github.manevolent.jbot.command;

import com.github.manevolent.jbot.command.executor.CommandExecutor;

import java.util.LinkedHashMap;
import java.util.Map;

public final class DefaultCommandManager extends CommandManager {
    private Map<String, CommandExecutor> executorMap = new LinkedHashMap<>();
    private final Object registrationLock = new Object();

    public Map<String, CommandExecutor> getExecutors() {
        return executorMap;
    }

    @Override
    public Registration registerExecutor(String label, CommandExecutor executor) {
        synchronized (registrationLock) {
            if (executorMap.containsKey(label))
                throw new IllegalArgumentException("command " + label + " already exists.");
            executorMap.put(label, executor);
            return new Registration(executor, label);
        }
    }

    @Override
    public void unregisterExecutor(String label) {
        synchronized (registrationLock) {
            executorMap.remove(label);
        }
    }

    @Override
    public CommandExecutor getExecutor(String label) {
        return executorMap.get(label);
    }
}
