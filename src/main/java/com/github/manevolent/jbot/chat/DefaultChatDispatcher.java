package com.github.manevolent.jbot.chat;

import com.github.manevolent.jbot.JBot;
import com.github.manevolent.jbot.chat.exception.ChatException;
import com.github.manevolent.jbot.command.CommandMessage;
import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.conversation.Conversation;
import com.github.manevolent.jbot.event.EventExecutionException;
import com.github.manevolent.jbot.event.chat.ChatMessageReceivedEvent;
import com.github.manevolent.jbot.event.chat.ChatUnknownUserEvent;
import com.github.manevolent.jbot.user.UserAssociation;

import java.util.concurrent.Future;

public class DefaultChatDispatcher implements ChatDispatcher {
    private final JBot bot;

    public DefaultChatDispatcher(JBot bot) {
        this.bot = bot;
    }

    @Override
    public void execute(ReceivedChatMessage chatMessage) throws ChatException {
        try {
            Future<?> future = executeAsync(chatMessage);
            if (future == null) return;

            future.get();
        } catch (Exception e) {
            throw new ChatException(e);
        }
    }

    @Override
    public Future<?> executeAsync(ReceivedChatMessage chatMessage) {
        ChatSender sender = chatMessage.getSender();
        Chat chat = sender.getChat();
        if (chat == null) throw new NullPointerException("chat");

        ReceivedChatMessage commandMessage = chat.parseCommand(chatMessage);
        if (commandMessage != null) {
            String platformSpecificId = sender.getUsername();
            UserAssociation userAssociation = chat.getPlatform().getUserAssocation(platformSpecificId);
            if (userAssociation == null) {
                try {
                    bot.getEventDispatcher().execute(new ChatUnknownUserEvent(this, commandMessage));
                } catch (EventExecutionException e) {
                    throw new RuntimeException(e);
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
