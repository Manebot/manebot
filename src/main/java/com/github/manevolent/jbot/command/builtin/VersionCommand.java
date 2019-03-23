package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.Bot;
import com.github.manevolent.jbot.BuildInformation;
import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;

public class VersionCommand extends AnnotatedCommandExecutor {
    private final Bot bot;

    public VersionCommand(Bot bot) {
        this.bot = bot;
    }

    @Command(description = "Gets verison information", permission = "system.version")
    public void version(CommandSender sender) throws CommandExecutionException {
        sender.details(builder -> builder.name("Project").key(BuildInformation.getName())
                .item("Version", BuildInformation.getVersion())
                .item("API version", BuildInformation.getApiVersion())
                .item("VCS commit", BuildInformation.getId())
                .item("Timestamp", BuildInformation.getTimestamp())
                .build()
        ).send();
    }

    @Override
    public String getDescription() {
        return "Gets version information";
    }

}
