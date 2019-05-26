package io.manebot.command.builtin;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.CommandExecutor;

public class EchoCommand implements CommandExecutor {
    @Override
    public void execute(CommandSender sender, String label, String[] args) throws CommandExecutionException {
        sender.sendMessage(String.join(" ", args));
    }

    @Override
    public String getDescription() {
        return "Echos sent messages";
    }
}
