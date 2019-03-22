package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.chat.Chat;
import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentLabel;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentPage;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentString;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.platform.PlatformManager;
import com.github.manevolent.jbot.platform.PlatformUser;
import com.github.manevolent.jbot.user.User;

import java.util.Comparator;
import java.util.stream.Collectors;

public class ChatCommand extends AnnotatedCommandExecutor {
    private final PlatformManager platformManager;

    public ChatCommand(PlatformManager platformManager) {
        this.platformManager = platformManager;
    }

    @Command(description = "Lists chats", permission = "system.chat.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentString.Argument(label = "platform id") String platformId,
                     @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {
        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null)
            throw new CommandArgumentException("Platform not found.");

        if (!platform.isConnected())
            throw new CommandArgumentException("Platform is not connected.");

        sender.list(
                Chat.class,
                builder -> builder.direct(platform.getConnection().getChats()
                        .stream()
                        .sorted(Comparator.comparing(Chat::getId))
                        .collect(Collectors.toList()))
                        .page(page)
                        .responder((sender1, chat1) -> chat1.getId() + " " +
                                (chat1.isConnected() ? "(connected)" : "(disconnected)"))
                        .build()
        ).send();
    }

    @Command(description = "Gets chat information", permission = "system.chat.info")
    public void info(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "info") String info,
                     @CommandArgumentString.Argument(label = "platform id") String platformId,
                     @CommandArgumentString.Argument(label = "chat id") String chatId)
            throws CommandExecutionException {
        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null)
            throw new CommandArgumentException("Platform not found.");

        if (!platform.isConnected())
            throw new CommandArgumentException("Platform is not connected.");

        Chat chatInstance = platform.getConnection().getChat(chatId);

        sender.details(builder -> {
            builder.name("Chat").key(chatInstance.getId());
            builder.item("Platform", platform.getId());

            if (chatInstance.isConnected()) {
                builder.item("Connected", "true");
                builder.item("Private", Boolean.toString(chatInstance.isPrivate()));
                builder.item("Buffered", Boolean.toString(chatInstance.isBuffered()));
                builder.item("Command prefixes", chatInstance.getCommandPrefixes());
                builder.item("Platform users", chatInstance.getPlatformUsers()
                        .stream().map(PlatformUser::getNickname).collect(Collectors.toList()));
                builder.item("Bot users", chatInstance.getUsers()
                        .stream().map(User::getDisplayName).collect(Collectors.toList()));
            } else {
                builder.item("Connected", "false");
            }

            return builder.build();
        }).send();
    }

    @Override
    public String getDescription() {
        return "Manages chats";
    }

}
