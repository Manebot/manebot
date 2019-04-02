package io.manebot.command;

import io.manebot.command.exception.CommandExecutionException;
import io.manebot.event.EventDispatcher;
import io.manebot.event.EventExecutionException;
import io.manebot.event.chat.CommandMessageReceivedEvent;
import io.manebot.user.User;
import com.google.common.collect.MapMaker;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Future;
import java.util.function.Function;

public class DefaultCommandDispatcher<S extends CommandShell> implements CommandDispatcher {
    private static final int queueSize = 3;

    private final Function<User, S> shellFactory;
    private final EventDispatcher eventDispatcher;

    public DefaultCommandDispatcher(Function<User, S> shellFactory,
                                    EventDispatcher eventDispatcher) {
        this.shellFactory = shellFactory;
        this.eventDispatcher = eventDispatcher;
    }

    @Override
    public S getShell(User user) {
        return shellFactory.apply(user);
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
    public Future<Boolean> executeAsync(CommandMessage commandMessage) {
        try {
            eventDispatcher.execute(new CommandMessageReceivedEvent(this, commandMessage));
        } catch (EventExecutionException e) {
            throw new RuntimeException(e);
        }

        return getShell(commandMessage.getSender().getUser()).executeAsync(commandMessage);
    }

    public static class CachedShellFactory<S extends CommandShell> implements Function<User, S> {
        private final ConcurrentMap<User, S> executors = new MapMaker().weakKeys().makeMap();
        private final Function<User, S> function;

        public CachedShellFactory(Function<User, S> function) {
            this.function = function;
        }

        @Override
        public S apply(User user) {
            S shell = executors.computeIfAbsent(user, function);
            while (!shell.isOpen()) executors.put(user, shell = function.apply(user));
            return shell;
        }
    }
}
