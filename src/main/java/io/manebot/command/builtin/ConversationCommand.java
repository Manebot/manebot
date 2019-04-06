package io.manebot.command.builtin;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.command.search.CommandArgumentSearch;
import io.manebot.conversation.Conversation;
import io.manebot.conversation.ConversationProvider;
import io.manebot.database.Database;
import io.manebot.database.model.Group;
import io.manebot.database.model.Platform;
import io.manebot.database.search.Search;
import io.manebot.database.search.SearchHandler;
import io.manebot.database.search.SearchOperator;
import io.manebot.database.search.handler.ComparingSearchHandler;
import io.manebot.database.search.handler.SearchHandlerPropertyContains;
import io.manebot.database.search.handler.SearchHandlerPropertyEquals;
import io.manebot.database.search.handler.SearchHandlerPropertyIn;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.stream.Collectors;

public class ConversationCommand extends AnnotatedCommandExecutor {
    private final ConversationProvider conversationProvider;
    private final SearchHandler<io.manebot.database.model.Conversation> searchHandler;

    public ConversationCommand(ConversationProvider conversationProvider, Database database) {
        this.conversationProvider = conversationProvider;
        this.searchHandler = database.createSearchHandler(io.manebot.database.model.Conversation.class)
                .string(new SearchHandlerPropertyContains("id"))
                .argument("platform", new SearchHandlerPropertyEquals(root -> root.get("platform").get("id")))
                .build();
    }

    @Command(description = "Searches conversations", permission = "system.conversation.search")
    public void search(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "search") String search,
                       @CommandArgumentSearch.Argument Search query)
            throws CommandExecutionException {
        try {
            sender.list(
                    io.manebot.database.model.Conversation.class,
                    searchHandler.search(query, 6),
                    (textBuilder, conversation) -> textBuilder.append(conversation.getId())
            ).send();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Command(description = "Lists conversations", permission = "system.conversation.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {

        sender.list(
                Conversation.class,
                builder -> builder.direct(
                        conversationProvider.getConversations()
                        .stream()
                        .sorted(Comparator.comparing(Conversation::getId))
                        .collect(Collectors.toList()))
                        .page(page)
                        .responder((textBuilder, conversation) -> textBuilder.append(conversation.getId()))
                        .build()
        ).send();
    }

    @Command(description = "Gets current chat information", permission = "system.conversation.info")
    public void info(CommandSender sender) throws CommandExecutionException {
        info(sender, sender.getConversation());
    }

    @Command(description = "Gets conversation information", permission = "system.conversation.info")
    public void info(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "info") String info,
                     @CommandArgumentString.Argument(label = "conversation id") String conversationId)
            throws CommandExecutionException {
        Conversation conversation;

        try {
            conversation = conversationProvider.getConversationById(conversationId);
        } catch (IllegalArgumentException e) {
            throw new CommandArgumentException(e);
        }

        if (conversation == null)
            throw new CommandArgumentException("Conversation not found.");
        info(sender, conversation);
    }

    private void info(CommandSender sender, Conversation conversation) throws CommandExecutionException {
        sender.details(builder -> {
            builder.name("Conversation").key(conversation.getId());
            builder.item("Platform", conversation.getPlatform().getId());
            builder.item("Connected", Boolean.toString(conversation.isConnected()));

            return builder.build();
        }).send();
    }

    @Override
    public String getDescription() {
        return "Manages chats";
    }

}
