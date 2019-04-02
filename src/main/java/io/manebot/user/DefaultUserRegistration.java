package io.manebot.user;

import io.manebot.DefaultBot;
import io.manebot.chat.ChatMessage;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;

public class DefaultUserRegistration implements UserRegistration {
    private final DefaultBot DefaultBot;

    public DefaultUserRegistration(DefaultBot DefaultBot) {
        this.DefaultBot = DefaultBot;
    }

    @Override
    public UserAssociation register(ChatMessage chatMessage) throws CommandExecutionException {
        throw new CommandArgumentException("Hey you're stupid");
    }
}
