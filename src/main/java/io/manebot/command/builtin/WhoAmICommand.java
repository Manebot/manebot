package io.manebot.command.builtin;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;

public class WhoAmICommand extends AnnotatedCommandExecutor {
    @Command(description = "Gets your username")
    public void whoami(CommandSender sender) throws CommandExecutionException {
        sender.sendMessage(sender.getDisplayName() + " (" + sender.getUsername() + ")");
    }

    @Override
    public String getDescription() {
        return "Gets your username";
    }

}
