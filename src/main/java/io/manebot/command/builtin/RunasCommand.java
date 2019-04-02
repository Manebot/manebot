package io.manebot.command.builtin;

import io.manebot.chat.*;
import io.manebot.command.CommandDispatcher;
import io.manebot.command.CommandMessage;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentFollowing;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.conversation.Conversation;
import io.manebot.platform.PlatformUser;
import io.manebot.user.User;
import io.manebot.user.UserManager;

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
                new BasicTextChatMessage(sender, command),
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
