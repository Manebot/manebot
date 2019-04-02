package io.manebot.command.builtin;

import io.manebot.Bot;
import io.manebot.BuildInformation;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;

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
