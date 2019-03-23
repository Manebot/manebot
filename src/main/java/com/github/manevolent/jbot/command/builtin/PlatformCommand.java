package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentLabel;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentPage;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentString;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.platform.PlatformManager;

import java.util.Comparator;
import java.util.stream.Collectors;

public class PlatformCommand extends AnnotatedCommandExecutor {
    private final PlatformManager platformManager;

    public PlatformCommand(PlatformManager platformManager) {
        this.platformManager = platformManager;
    }

    @Command(description = "Connects a platform", permission = "system.platform.connect")
    public void connect(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "connect") String connect,
                        @CommandArgumentString.Argument(label = "platform id") String platformId)
            throws CommandExecutionException {
        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null)
            throw new CommandArgumentException("Platform not found.");

        if (platform.isConnected())
            throw new CommandArgumentException("Platform is already connected.");

        if (platform.getConnection() == null)
            throw new CommandArgumentException("Platform is not registered.");

        platform.getConnection().connect();

        if (platform.getConnection().isConnected())
            sender.sendMessage("Platform connected successfully.");
        else
            throw new CommandExecutionException("Platform did not connect after attempting to make a connection.");
    }

    @Command(description = "Disconnects a platform", permission = "system.platform.disconnect")
    public void disconnct(CommandSender sender,
                          @CommandArgumentLabel.Argument(label = "disconnect") String disconnect,
                          @CommandArgumentString.Argument(label = "platform id") String platformId)
            throws CommandExecutionException {
        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null)
            throw new CommandArgumentException("Platform not found.");

        if (!platform.isConnected())
            throw new CommandArgumentException("Platform is already disconnected.");

        if (platform.getConnection() == null)
            throw new CommandArgumentException("Platform is not registered.");

        try {
            platform.getConnection().disconnect();
        } catch (UnsupportedOperationException ex) {
            throw new CommandArgumentException("Platform does not support disconnecting.");
        }

        if (!platform.getConnection().isConnected())
            sender.sendMessage("Platform disconnected successfully.");
        else
            throw new CommandExecutionException(
                    "Platform did not disconnect after " +
                            "attempting to close its connection."
            );
    }

    @Command(description = "Lists platforms", permission = "system.platform.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {
        sender.list(
                Platform.class,
                builder -> builder
                        .direct(platformManager.getPlatforms()
                                .stream()
                                .sorted(Comparator.comparing(Platform::getId))
                                .collect(Collectors.toList()))
                        .page(page)
                        .responder((sender1, platform) -> platform.getId() + " " +
                                (platform.isConnected() ? "(connected)" : "(disconnected)"))
                        .build()
        ).send();
    }

    @Command(description = "Gets platform information", permission = "system.platform.info")
    public void info(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "info") String info,
                     @CommandArgumentString.Argument(label = "platform id") String platformId)
            throws CommandExecutionException {
        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null)
            throw new CommandArgumentException("Platform not found.");

        sender.details(builder -> {
            builder.name("Platform").key(platform.getId());

            if (platform.getPlugin() != null) {
                builder.item("Plugin", platform.getPlugin().getArtifact().getIdentifier().toString());
            } else {
                builder.item("Plugin", "(none)");
            }

            if (platform.getRegistration() != null) {
                builder.item("Registered", "true");
                builder.item("Connected", Boolean.toString(platform.isConnected()));
            } else {
                builder.item("Registered", "false");
            }

            return builder.build();
        }).send();
    }

    @Override
    public String getDescription() {
        return "Manages platforms";
    }

}
