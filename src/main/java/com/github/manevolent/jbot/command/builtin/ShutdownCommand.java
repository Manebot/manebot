package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.Bot;
import com.github.manevolent.jbot.BuildInformation;
import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandAccessException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;

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
