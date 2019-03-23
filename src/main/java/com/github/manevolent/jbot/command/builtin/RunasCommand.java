package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.chat.Chat;
import com.github.manevolent.jbot.chat.ChatMessage;
import com.github.manevolent.jbot.chat.ChatSender;
import com.github.manevolent.jbot.chat.ReceivedChatMessage;
import com.github.manevolent.jbot.command.CommandDispatcher;
import com.github.manevolent.jbot.command.CommandMessage;
import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentFollowing;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentString;
import com.github.manevolent.jbot.conversation.Conversation;
import com.github.manevolent.jbot.platform.PlatformUser;
import com.github.manevolent.jbot.user.User;
import com.github.manevolent.jbot.user.UserManager;

import java.util.Calendar;
import java.util.Date;

public class RunasCommand extends AnnotatedCommandExecutor {
    private final UserManager userManager;
    private final CommandDispatcher commandDispatcher;

    public RunasCommand(UserManager userManager, CommandDispatcher commandDispatcher) {
        this.userManager = userManager;
        this.commandDispatcher = commandDispatcher;
    }

    @Command(description = "Runs a command as another user", permission = "system.runas")
    public void ban(CommandSender sender,
                    @CommandArgumentString.Argument(label = "username") String username,
                    @CommandArgumentFollowing.Argument() String command)
            throws CommandExecutionException {
        User user = userManager.getUserByDisplayName(username);
        if (user == null) throw new CommandArgumentException("User not found");

        commandDispatcher.executeAsync(new CommandMessage(
                new ReceivedChatMessage() {
                    final Date date = Calendar.getInstance().getTime();

                    @Override
                    public ChatSender getSender() {
                        return sender;
                    }

                    @Override
                    public void delete() throws UnsupportedOperationException {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public void edit(ChatMessage chatMessage) throws UnsupportedOperationException {
                        throw new UnsupportedOperationException();
                    }

                    @Override
                    public Date getDate() {
                        return date;
                    }

                    @Override
                    public String getMessage() {
                        return command;
                    }
                },
                new PseudoCommandSender(sender.getPlatformUser(), sender.getChat(), sender, user)
        ));
    }

    @Override
    public String getDescription() {
        return "Runs a command as another user";
    }


    private static class PseudoCommandSender extends CommandSender {
        private final CommandSender parent;
        private final User user;

        public PseudoCommandSender(PlatformUser user, Chat chat, CommandSender parent, User user1) {
            super(user, chat);
            this.parent = parent;
            this.user = user1;
        }

        @Override
        public CommandSender getParent() {
            return parent;
        }

        @Override
        public Conversation getConversation() {
            return parent.getConversation();
        }

        @Override
        public User getUser() {
            return user;
        }
    }
}
