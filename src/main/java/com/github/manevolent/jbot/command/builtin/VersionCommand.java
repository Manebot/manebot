package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.Bot;
import com.github.manevolent.jbot.BuildInformation;
import com.github.manevolent.jbot.command.CommandManager;
import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.CommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.ChainPriority;
import com.github.manevolent.jbot.command.executor.chained.ChainState;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentFollowing;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentPage;
import com.github.manevolent.jbot.command.response.CommandListResponse;

import java.util.*;
import java.util.stream.Collectors;

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
