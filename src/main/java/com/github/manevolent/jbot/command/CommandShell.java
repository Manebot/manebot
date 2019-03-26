package com.github.manevolent.jbot.command;

import com.github.manevolent.jbot.command.exception.CommandAccessException;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.exception.CommandNotFoundException;
import com.github.manevolent.jbot.command.executor.CommandExecutor;
import com.github.manevolent.jbot.event.EventDispatcher;
import com.github.manevolent.jbot.event.EventExecutionException;
import com.github.manevolent.jbot.event.command.CommandExecutionEvent;
import com.github.manevolent.jbot.plugin.PluginException;
import com.github.manevolent.jbot.user.User;
import com.github.manevolent.jbot.user.UserBan;

import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

abstract class CommandShell {
    private static final Map<Class<? extends Throwable>, Class<? extends CommandExecutionException>>
            permittedExceptionClasses = new LinkedHashMap<>();
    {
        permittedExceptionClasses.put(IllegalArgumentException.class, CommandArgumentException.class);
        permittedExceptionClasses.put(SecurityException.class, CommandAccessException.class);
    }

    private final CommandManager commandManager;
    private final EventDispatcher eventDispatcher;

    protected CommandShell(CommandManager commandManager, EventDispatcher eventDispatcher) {
        this.commandManager = commandManager;
        this.eventDispatcher = eventDispatcher;
    }

    public abstract User getUser();

    protected void handleCommand(CommandMessage commandMessage) throws CommandExecutionException {
        String message = commandMessage.getMessage().split("\n")[0].trim();

        // Remove all double-spaces
        message = message.replace("\t", "\0");
        while (message.contains("\0\0")) message = message.replace("\0\0", "\0");

        // Get command label and its arguments
        String[] labelAndArguments = message.split("[ ]", 2);
        String label = labelAndArguments[0];
        String[] arguments = labelAndArguments.length > 1 ? labelAndArguments[1].split("[ ]") : new String[0];

        label = label.toLowerCase();


        try {
            // Find command associated with this label
            CommandExecutor executor = commandManager.getExecutor(label);
            if (executor == null) throw new CommandNotFoundException(label);

            try {
                eventDispatcher.execute(
                        new CommandExecutionEvent(this, executor, commandMessage.getSender(), commandMessage)
                );
            } catch (EventExecutionException e) {
                throw new CommandExecutionException(e);
            }

            UserBan ban = getUser().getBan();
            if (ban != null && !ban.isPardoned()) {
                if (ban.getReason() != null)
                    throw new CommandAccessException("You have been banned until " +
                            ban.getEnd() + " by " +
                            ban.getBanningUser().getDisplayName() + " (" + ban.getReason() + ")");
                else
                    throw new CommandAccessException("You have been banned until " +
                            ban.getEnd() + " by " +
                            ban.getBanningUser().getDisplayName());
            }

            if (executor.isBuffered() && commandMessage.getSender().getChat().isBuffered())
                commandMessage.getSender().begin();

            try {
                try {
                    executor.execute(commandMessage.getSender(), label, arguments);
                } catch (IllegalArgumentException e) {
                    throw new CommandArgumentException(e.getMessage());
                }
            } catch (ThreadDeath threadDeath) {
                throw new CommandAccessException("Command execution was forcefully stopped.", threadDeath);
            } catch (SecurityException e) { // Un-wrap security exceptions.
                throw new CommandAccessException(e);
            } catch (Throwable e) {
                // Unpack the exception down to a CommandExecutionException
                Throwable z = e;
                while (!(z instanceof CommandExecutionException)) {
                    if (z.getCause() != null) z = z.getCause();
                    else break;
                }
                if (z instanceof CommandExecutionException) e = z;

                // Unpack any CommandExecutionExceptions to their root causes. If the root cause is another CEX,
                // we allow its message to be printed. Otherwise, we must mute the message to prevent attacks on
                // exceptions.
                while (e instanceof CommandExecutionException) {
                    if (e.getCause() != null) e = e.getCause();
                    else throw (CommandExecutionException) e;
                }

                // If the exception is "permitted" (whitelisted), allow it to propagate to the end-user.
                Class<? extends CommandExecutionException> permittedExceptionClass =
                        permittedExceptionClasses.get(e.getClass());
                CommandExecutionException permittedException = null;
                if (permittedExceptionClass != null) {
                    try {
                        permittedException =
                                permittedExceptionClass
                                .getConstructor(String.class, Throwable.class) // message, cause (to propagate stacktrace)
                                .newInstance(e.getMessage(), e);
                    } catch (ReflectiveOperationException ex) {
                        throw new RuntimeException(ex); // Should not happen
                    }
                }
                if (permittedException != null) throw permittedException;

                // The exception isn't a part of the typical CommandExecutionException chain, and we must
                // mute the details. Could be a bug or command error deeper in the system we don't want to
                // reveal.
                Logger.getGlobal().log(
                        Level.SEVERE,
                        "Unhandled " + e.getClass().getName() + " thrown executing command",
                        e
                );

                commandMessage.getSender().sendMessage("There was an unexpected problem executing the command.");
            }
        } catch (CommandExecutionException e) { // User error that we can describe safely
            //Logger.getGlobal().log(Level.WARNING, "Problem executing command", e);
            commandMessage.getSender().sendMessage("Problem executing command: " + e.getMessage());
        } finally {
            commandMessage.getSender().end();

            getUser().setLastSeenDate(Calendar.getInstance().getTime());
        }
    }
}
