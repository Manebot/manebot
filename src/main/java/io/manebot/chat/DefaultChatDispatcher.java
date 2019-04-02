package io.manebot.chat;

import io.manebot.DefaultBot;
import io.manebot.chat.exception.ChatException;
import io.manebot.command.CommandMessage;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.conversation.Conversation;
import io.manebot.event.EventExecutionException;
import io.manebot.event.chat.ChatMessageReceivedEvent;
import io.manebot.event.chat.ChatUnknownUserEvent;

import io.manebot.user.UserAssociation;
import io.manebot.user.UserRegistration;

import java.util.concurrent.Future;

public class DefaultChatDispatcher implements ChatDispatcher {
    private final DefaultBot bot;

    public DefaultChatDispatcher(DefaultBot bot) {
        this.bot = bot;
    }

    @Override
    public void execute(ChatMessage chatMessage) throws ChatException {
        try {
            Future<?> future = executeAsync(chatMessage);
            if (future == null) return;

            future.get();
        } catch (Exception e) {
            throw new ChatException(e);
        }
    }

    @Override
    public Future<?> executeAsync(ChatMessage chatMessage) {
        ChatSender sender = chatMessage.getSender();
        Chat chat = sender.getChat();
        if (chat == null) throw new NullPointerException("chat");

        ChatMessage commandMessage = chat.parseCommand(chatMessage);
        if (commandMessage != null) {
            String platformSpecificId = sender.getUsername();
            UserAssociation userAssociation = chat.getPlatform().getUserAssocation(platformSpecificId);
            if (userAssociation == null) {
                try {
                    bot.getEventDispatcher().execute(new ChatUnknownUserEvent(this, commandMessage));
                } catch (EventExecutionException e) {
                    throw new RuntimeException(e);
                }

                UserRegistration registration =
                        chatMessage.getSender().getPlatformUser().getConnection().getUserRegistration();

                if (registration != null && registration.canRegister(chatMessage.getSender().getPlatformUser())) {
                    try {
                        UserAssociation association = registration.register(chatMessage);
                        if (association != null) {
                            sender.sendMessage(
                                    "You have been registered as " + association.getUser().getDisplayName() + "."
                            );
                        }
                    } catch (CommandArgumentException e) {
                        sender.sendMessage("There was a problem registering your user: " + e.getMessage());
                    } catch (CommandExecutionException e) {
                        sender.sendMessage("There was an unexpected problem registering your user.");
                    }
                }

                return null;
            }

            Conversation conversation = bot.getConversationProvider().getConversationByChat(chat);
            CommandSender commandSender = userAssociation.getUser().createSender(
                    conversation,
                    chatMessage.getSender().getPlatformUser()
            );

            return bot.getCommandDispatcher().executeAsync(new CommandMessage(commandMessage, commandSender));
        } else
            return bot.getEventDispatcher().executeAsync(new ChatMessageReceivedEvent(this, chatMessage));
    }
}
