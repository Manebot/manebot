package io.manebot.command.builtin;

import io.manebot.chat.BasicTextChatMessage;
import io.manebot.command.CommandDispatcher;
import io.manebot.command.CommandMessage;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentFollowing;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.conversation.Conversation;
import io.manebot.conversation.ConversationProvider;
import io.manebot.user.User;
import io.manebot.user.UserManager;

public class RuninCommand extends AnnotatedCommandExecutor {
    private final ConversationProvider conversationProvider;
    private final CommandDispatcher commandDispatcher;

    public RuninCommand(ConversationProvider conversationProvider, CommandDispatcher commandDispatcher) {
        this.conversationProvider = conversationProvider;
        this.commandDispatcher = commandDispatcher;
    }

    @Command(description = "Runs a command in another conversation", permission = "system.runin")
    public void ban(CommandSender sender,
                    @CommandArgumentString.Argument(label = "conversation ID") String conversationId,
                    @CommandArgumentFollowing.Argument() String command)
            throws CommandExecutionException {
        Conversation conversation = conversationProvider.getConversationById(conversationId);
        if (conversation == null)
            throw new CommandArgumentException("Conversation not found");

        commandDispatcher.executeAsync(new CommandMessage(
                new BasicTextChatMessage(sender, command),
                new PseudoCommandSender(sender, conversation)
        ));
    }

    @Override
    public String getDescription() {
        return "Runs a command in another conversation";
    }

    private static class PseudoCommandSender extends CommandSender {
        private final CommandSender parent;
        private final Conversation conversation;

        public PseudoCommandSender(CommandSender parent, Conversation conversation) {
            super(parent.getPlatformUser(), conversation.getChat());
            this.conversation = conversation;
            this.parent = parent;
        }

        @Override
        public CommandSender getParent() {
            return parent;
        }

        @Override
        public Conversation getConversation() {
            return conversation;
        }

        @Override
        public User getUser() {
            return parent.getUser();
        }
    }
}
