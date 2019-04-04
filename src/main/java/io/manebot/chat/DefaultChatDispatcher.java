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
import io.manebot.user.UserType;

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
            if (userAssociation == null)
                return bot.getEventDispatcher().executeAsync(new ChatUnknownUserEvent(this, commandMessage));

            if (userAssociation.getUser().getType() == UserType.ANONYMOUS)
                throw new SecurityException(
                        userAssociation.getUser().getName()
                        + " is an anonymous user and cannot run commands sent from a chat"
                );

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
