package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;

public class PingCommand extends AnnotatedCommandExecutor {
    public PingCommand() {}

    @Command(description = "Pings the system")
    public void ping(CommandSender sender) {

    }

    @Override
    public String getDescription() {
        return "Pings the system";
    }

}
