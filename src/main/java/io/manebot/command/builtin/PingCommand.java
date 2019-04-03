package io.manebot.command.builtin;

import io.manebot.command.CommandSender;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;

public class PingCommand extends AnnotatedCommandExecutor {
    public PingCommand() {}

    @Command(description = "Pings the system")
    public void ping(CommandSender sender) {
        sender.sendMessage("Ping reply.");
    }

    @Override
    public String getDescription() {
        return "Pings the system";
    }

}
