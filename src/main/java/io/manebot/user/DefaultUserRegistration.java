package io.manebot.user;

import io.manebot.Bot;
import io.manebot.DefaultBot;
import io.manebot.chat.ChatMessage;
import io.manebot.chat.TextStyle;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.platform.Platform;

import java.util.Arrays;
import java.util.EnumSet;

public class DefaultUserRegistration implements UserRegistration {
    private final Bot bot;

    public DefaultUserRegistration(Bot bot) {
        this.bot = bot;
    }

    @Override
    public UserAssociation register(ChatMessage chatMessage) throws CommandExecutionException {
        final String username = chatMessage.getSender().getDisplayName().replace(" ", "");
        final Platform platform = chatMessage.getSender().getPlatformUser().getPlatform();

        UserAssociation association = null;
        String[] arguments = chatMessage.getRawMessage().split(" ", 2);

        if ("register".equals(arguments[0].toLowerCase())) {
            String attemptedUsername =
                    (arguments.length == 2 && arguments[1].trim().length() > 0 ? arguments[1] : username)
                            .trim().toLowerCase();

            User user = bot.getUserManager().getUserByDisplayName(attemptedUsername);

            if (user != null) {
                try {
                    user.prompt(builder -> builder
                            .setName("User association request")
                            .setDescription(textBuilder ->
                                    textBuilder
                                            .append(chatMessage.getSender().getDisplayName(), EnumSet.of(TextStyle.BOLD))
                                            .append(" is trying to associate their ")
                                            .append(chatMessage.getSender().getChat().getPlatform().getId())
                                            .append(" account to this user")
                            ).setCallback((prompt) -> {
                                User confirmedUser = prompt.getUser();

                                confirmedUser.createAssociation(
                                        chatMessage.getSender().getPlatformUser().getPlatform(),
                                        chatMessage.getSender().getPlatformUser().getId()
                                );

                                chatMessage.getSender().sendFormattedMessage(textBuilder -> textBuilder
                                        .append("You have confirmed the registration. You been registered as \"")
                                        .append(confirmedUser.getDisplayName(), EnumSet.of(TextStyle.ITALICS))
                                        .append("\".")
                                );
                            })
                    );
                } catch (IllegalStateException ex) {
                    throw new CommandArgumentException(ex.getMessage());
                }

                chatMessage.getSender().sendFormattedMessage(textBuilder -> textBuilder
                        .append("Another user already exists by the name \"")
                        .append(attemptedUsername, EnumSet.of(TextStyle.ITALICS))
                        .append("\" and a prompt has been sent to them. ")
                        .append("To confirm this registration, send \"")
                        .append(".confirm", EnumSet.of(TextStyle.ITALICS))
                        .append("\" from an authorized endpoint.")
                );
            } else {
                if (attemptedUsername.length() <= 0)
                    throw new CommandArgumentException("Desired username is too short.");

                if (attemptedUsername.chars().anyMatch(character -> !Character.isLetterOrDigit((char) character)))
                    throw new CommandArgumentException("Desired username is not alphanumeric.");

                user = bot.getUserManager().createUser(attemptedUsername, UserType.COMMON);

                UserGroup userGroup = platform.getDefaultGroup();
                if (userGroup != null) user.addGroup(userGroup);

                association = user.createAssociation(
                        chatMessage.getSender().getPlatformUser().getPlatform(),
                        chatMessage.getSender().getPlatformUser().getId()
                );

                final String resultingUsername = user.getDisplayName();
                chatMessage.getSender().sendFormattedMessage(textBuilder -> textBuilder
                        .append("You have been registered as \"")
                        .append(resultingUsername, EnumSet.of(TextStyle.ITALICS))
                        .append("\".")
                );
            }
        } else {
            chatMessage.getSender().sendFormattedMessage(textBuilder -> textBuilder
                    .append("You are not yet recognized. To register, reply with \"")
                    .append(".register")
                    .append("\". Your username will be \"")
                    .append(username)
                    .append("\" if you do so. If you would like to specify a username yourself, you can ")
                    .append("reply with \"")
                    .append(".register ")
                    .append("[username]")
                    .append("\".")
            );
        }

        return association;
    }

    private static void checkAlphanumeric(String s) throws CommandArgumentException {
        for (char c : s.toCharArray())
            if (!Character.isLetterOrDigit(c))
                throw new CommandArgumentException("'" + c + "' is not alphanumeric.");
    }
}
