package com.github.manevolent.jbot.command;

import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.user.User;
import com.google.common.collect.MapMaker;

import java.util.Map;
import java.util.concurrent.Future;

public class DefaultCommandDispatcher implements CommandDispatcher {
    private final Map<User, AsyncCommandShell> executors = new MapMaker().weakKeys().makeMap();

    private final CommandManager commandManager;

    public DefaultCommandDispatcher(CommandManager commandManager) {
        this.commandManager = commandManager;
    }

    @Override
    public void execute(CommandMessage commandMessage) throws CommandExecutionException {
        try {
            executeAsync(commandMessage).get();
        } catch (Exception e) {
            throw new CommandExecutionException(e);
        }
    }

    @Override
    public Future<?> executeAsync(CommandMessage commandMessage) {
        AsyncCommandShell execution;

        synchronized (executors) {
            execution = executors.computeIfAbsent(
                    commandMessage.getSender().getUser(),
                    user -> new AsyncCommandShell(commandManager, user, 3)
            );
        }

        execution.setRunning(true);

        return execution.submit(commandMessage);
    }
}
