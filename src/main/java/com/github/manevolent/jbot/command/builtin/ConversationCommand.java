package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentLabel;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentPage;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentString;
import com.github.manevolent.jbot.command.search.CommandArgumentSearch;
import com.github.manevolent.jbot.conversation.Conversation;
import com.github.manevolent.jbot.conversation.ConversationProvider;
import com.github.manevolent.jbot.database.Database;
import com.github.manevolent.jbot.database.model.Group;
import com.github.manevolent.jbot.database.model.Platform;
import com.github.manevolent.jbot.database.search.Search;
import com.github.manevolent.jbot.database.search.SearchHandler;
import com.github.manevolent.jbot.database.search.SearchOperator;
import com.github.manevolent.jbot.database.search.handler.ComparingSearchHandler;
import com.github.manevolent.jbot.database.search.handler.SearchHandlerPropertyContains;
import com.github.manevolent.jbot.database.search.handler.SearchHandlerPropertyEquals;
import com.github.manevolent.jbot.database.search.handler.SearchHandlerPropertyIn;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.stream.Collectors;

public class ConversationCommand extends AnnotatedCommandExecutor {
    private final ConversationProvider conversationProvider;
    private final SearchHandler<com.github.manevolent.jbot.database.model.Conversation> searchHandler;

    public ConversationCommand(ConversationProvider conversationProvider, Database database) {
        this.conversationProvider = conversationProvider;
        this.searchHandler = database.createSearchHandler(com.github.manevolent.jbot.database.model.Conversation.class)
                .string(new SearchHandlerPropertyContains("name"))
                .argument("owner", new ComparingSearchHandler(
                        new SearchHandlerPropertyEquals(root -> root.get("owningUser").get("displayName")),
                        new SearchHandlerPropertyEquals(root -> root.get("owningUser").get("username")),
                        SearchOperator.INCLUDE))
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
                    com.github.manevolent.jbot.database.model.Conversation.class,
                    searchHandler.search(query, 6),
                    (sender1, conversation) -> conversation.getId()
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
                        .responder((sender1, conversation) -> conversation.getId())
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
