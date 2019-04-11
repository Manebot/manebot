package io.manebot.command;

import io.manebot.command.exception.CommandExecutionException;
import io.manebot.event.EventDispatcher;
import io.manebot.lambda.ThrowingFunction;
import io.manebot.user.User;
import io.manebot.virtual.Virtual;
import io.manebot.virtual.VirtualProcess;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AsyncCommandShell extends AbstractCommandShell implements Runnable {
    private final long maximumIdleTime = 120_000L;
    private static final int defaultBacklog = 3;

    private final Runnable complete;
    private final User user;
    private final BlockingDeque<AsyncCommand> queue;

    private final VirtualProcess process;

    private boolean running = false;

    AsyncCommandShell(CommandManager commandManager, EventDispatcher eventDispatcher,
                      User user, int backlog, Runnable complete) {
        super(commandManager, eventDispatcher);

        this.complete = complete;
        this.user = user;
        this.queue = new LinkedBlockingDeque<>(backlog);
        this.process = Virtual.getInstance().create(this);
    }

    public boolean isRunning() {
        return running;
    }

    @Override
    public User getUser() {
        return user;
    }

    public synchronized void setRunning(boolean b) {
        if (running != b) {
            if (b) {
                if (!process.isRunning()) {
                    process.changeUser(getUser());
                    process.start();
                }
            } else if (process.isRunning() && !process.isCallerSelf()) {
                process.interrupt();
            }

            this.running = b;

            if (!b) complete.run();
        }
    }

    @Override
    public void execute(CommandMessage commandMessage) throws CommandExecutionException {
        try {
            executeAsync(commandMessage).get();
        } catch (ExecutionException ee) {
            if (ee.getCause() instanceof CommandExecutionException)
                throw (CommandExecutionException) ee.getCause();

            throw new CommandExecutionException(ee);
        } catch (Exception e) {
            throw new CommandExecutionException(e);
        }
    }

    @Override
    public Future<Boolean> executeAsync(CommandMessage commandMessage) {
        AsyncCommand command;

        if (!isRunning()) throw new IllegalStateException("shell is not running");

        if (!queue.add(command = new AsyncCommand(commandMessage)))
            throw new IllegalStateException("shell queue is full");

        return command.getFuture();
    }

    @Override
    public boolean isOpen() {
        return process.isRunning() && running;
    }

    @Override
    public void ensureOpen() {
        synchronized (process) {
            if (!isOpen()) throw new IllegalStateException();
        }
    }

    @Override
    public void run() {
        try {
            long lastMessage = System.currentTimeMillis();
            AsyncCommand asyncCommand;

            while (isRunning()) {
                long wait = maximumIdleTime - (System.currentTimeMillis() - lastMessage);

                try {
                    asyncCommand = queue.poll(Math.max(0, wait), TimeUnit.MILLISECONDS);
                } catch (InterruptedException | IllegalMonitorStateException ex) {
                    break;
                }

                if (asyncCommand == null) break;

                try {
                    handleCommand(asyncCommand.getMessage());
                } catch (java.lang.ThreadDeath ex) {
                    break;
                } catch (Throwable ex) {
                    Logger.getGlobal().log(
                            Level.SEVERE,
                            "Unexpected problem handling chat message in shell: " + getUser().getName(),
                            ex
                    );

                    break; // Kill shell
                } finally {
                    asyncCommand.getMessage().getSender().end();
                    asyncCommand.getFuture().complete(true);
                }
            }
        } finally {
            setRunning(false);
        }
    }

    public static class ShellFactory implements ThrowingFunction<User, AsyncCommandShell, Exception> {
        private final CommandManager commandManager;
        private final EventDispatcher eventDispatcher;
        private final Map<User, AsyncCommandShell> shells = new LinkedHashMap<>();

        public ShellFactory(CommandManager commandManager, EventDispatcher eventDispatcher) {
            this.commandManager = commandManager;
            this.eventDispatcher = eventDispatcher;
        }

        @Override
        public AsyncCommandShell applyChecked(User user) throws Exception {
            AsyncCommandShell shell = shells.computeIfAbsent(user, key -> new AsyncCommandShell(
                    commandManager,
                    eventDispatcher,
                    key,
                    defaultBacklog,
                    () -> shells.remove(key)
            ));

            shell.setRunning(true);

            return shell;
        }
    }
}