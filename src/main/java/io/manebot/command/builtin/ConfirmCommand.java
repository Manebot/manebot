package io.manebot.command.builtin;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.user.UserPrompt;

public class ConfirmCommand extends AnnotatedCommandExecutor {

    @Command(description = "Confirms pending prompts")
    public void confirm(CommandSender sender) throws CommandExecutionException {
        UserPrompt prompt = sender.getUser().getPrompt();
        if (prompt == null) throw new CommandArgumentException("There is no pending prompt to confirm.");
        prompt.complete();
        sender.sendMessage("Prompt confirmed successfully.");
    }

    @Override
    public String getDescription() {
        return "Confirms pending prompts";
    }

}
