package com.github.manevolent.jbot.command;

import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.event.EventDispatcher;
import com.github.manevolent.jbot.event.EventExecutionException;
import com.github.manevolent.jbot.event.chat.CommandMessageReceivedEvent;

import com.github.manevolent.jbot.user.User;
import com.google.common.collect.MapMaker;

import java.util.Map;
import java.util.concurrent.Future;

public class DefaultCommandDispatcher implements CommandDispatcher {
    private static final int queueSize = 3;

    private final Map<User, AsyncCommandShell> executors = new MapMaker().weakKeys().makeMap();

    private final CommandManager commandManager;
    private final EventDispatcher eventDispatcher;

    public DefaultCommandDispatcher(CommandManager commandManager, EventDispatcher eventDispatcher) {
        this.commandManager = commandManager;
        this.eventDispatcher = eventDispatcher;
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
        try {
            eventDispatcher.execute(new CommandMessageReceivedEvent(this, commandMessage));
        } catch (EventExecutionException e) {
            throw new RuntimeException(e);
        }

        AsyncCommandShell execution;

        synchronized (executors) {
            execution = executors.computeIfAbsent(
                    commandMessage.getSender().getUser(),
                    user -> new AsyncCommandShell(commandManager, eventDispatcher,
                            user, queueSize, () -> {
                        synchronized (executors) {
                            executors.remove(user);
                        }
                    })
            );
        }

        execution.setRunning(true);

        return execution.submit(commandMessage);
    }
}
