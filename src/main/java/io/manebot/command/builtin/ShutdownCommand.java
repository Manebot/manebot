package io.manebot.command.builtin;

import io.manebot.Bot;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandAccessException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;

public class ShutdownCommand extends AnnotatedCommandExecutor {
    private final Bot bot;

    public ShutdownCommand(Bot bot) {
        this.bot = bot;
    }

    @Command(description = "Shuts down the bot", permission = "system.shutdown")
    public void shutdown(CommandSender sender) throws CommandExecutionException {
        try {
            bot.stop();
        } catch (IllegalAccessException e) {
            throw new CommandAccessException(e.getMessage());
        }
    }

    @Override
    public String getDescription() {
        return "Shuts down the bot";
    }

}
