package com.github.manevolent.jbot.command;

import com.github.manevolent.jbot.command.exception.CommandAccessException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.exception.CommandNotFoundException;
import com.github.manevolent.jbot.command.executor.CommandExecutor;
import com.github.manevolent.jbot.user.User;

import java.util.logging.Level;
import java.util.logging.Logger;

abstract class CommandShell {
    private final CommandManager commandManager;

    protected CommandShell(CommandManager commandManager) {
        this.commandManager = commandManager;
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

        // Find command associated with this label
        CommandExecutor executor = commandManager.getExecutor(label);
        if (executor == null) throw new CommandNotFoundException(label);

        try {
            if (executor.isBuffered() && commandMessage.getSender().getChat().isBuffered())
                commandMessage.getSender().begin();

            try {
                executor.execute(commandMessage.getSender(), label, arguments);
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
            Logger.getGlobal().log(Level.WARNING, "Problem executing command", e);
            commandMessage.getSender().sendMessage("Problem executing command: " + e.getMessage());
        } finally {
            commandMessage.getSender().end();
        }
    }
}
