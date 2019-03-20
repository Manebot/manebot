package com.github.manevolent.jbot.command;

import com.github.manevolent.jbot.user.User;
import com.github.manevolent.jbot.virtual.Virtual;
import com.github.manevolent.jbot.virtual.VirtualProcess;

import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AsyncCommandShell extends CommandShell implements Runnable {
    private final long maximumIdleTime = 120_000L;

    private final Runnable complete;
    private final User user;
    private final BlockingDeque<AsyncCommand> queue;

    private VirtualProcess process;
    private boolean running = false;

    AsyncCommandShell(CommandManager commandManager, User user, int backlog, Runnable complete) {
        super(commandManager);

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
            this.running = b;

            if (b) {
                if (process.isRunning()) return;

                process.changeUser(getUser());
                process.start();
            } else if (process.isRunning() && !process.isCallerSelf()) {
                process.interrupt();
            }

            if (!b) complete.run();
        }
    }

    public Future<Boolean> submit(CommandMessage commandMessage) {
        AsyncCommand command;

        if (!isRunning()) throw new IllegalStateException("shell is not running");

        if (!queue.add(command = new AsyncCommand(commandMessage)))
            throw new IllegalStateException("shell queue is full");

        return command.getFuture();
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

                if (asyncCommand == null) throw new NullPointerException();

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
}