package io.manebot.command.builtin;

import io.manebot.chat.TextBuilder;
import io.manebot.command.CommandSender;
import io.manebot.command.alias.AliasManager;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentFollowing;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.executor.chained.argument.CommandArgumentString;

import io.manebot.database.model.CommandAlias;

public class AliasCommand extends AnnotatedCommandExecutor {
    private final AliasManager aliasManager;

    public AliasCommand(AliasManager aliasManager) {
        this.aliasManager = aliasManager;
    }

    @Command(description = "Creates an alias", permission = "system.command.alias.create")
    public void create(CommandSender sender,
                    @CommandArgumentLabel.Argument(label = "create") String create,
                    @CommandArgumentString.Argument(label = "label") String label,
                    @CommandArgumentFollowing.Argument() String alias)
            throws CommandExecutionException {
        CommandAlias commandAlias = aliasManager.createAlias(label, alias);

        try {
            aliasManager.reregisterAliases();
        } catch (Exception e) {
            commandAlias.delete();
            throw new CommandExecutionException(e);
        }

        sender.sendMessage("Alias created successfully.");
    }

    @Command(description = "Creates an alias", permission = "system.command.alias.remove")
    public void remove(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "remove") String remove,
                       @CommandArgumentString.Argument(label = "label") String label)
            throws CommandExecutionException {
        CommandAlias alias = aliasManager.getAlias(label);
        if (alias == null) throw new CommandExecutionException("Alias does not exist.");
        alias.delete();
        aliasManager.reregisterAliases();
        sender.sendMessage("Alias removed successfully.");
    }

    @Command(description = "Creates an alias", permission = "system.command.alias.list")
    public void list(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "list") String list,
                       @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        sender.sendList(CommandAlias.class, builder -> {
            builder.page(page);
            builder.direct(aliasManager.getAliases());
            builder.responder((textBuilder, o) -> textBuilder.append(o.getLabel() + ": " + o.getAlias()));
        });
    }

    @Command(description = "Gets command alias information", permission = "system.command.alias.info")
    public void info(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "info") String info,
                     @CommandArgumentString.Argument(label = "label") String label)
            throws CommandExecutionException {
        CommandAlias alias = aliasManager.getAlias(label);
        if (alias == null) throw new CommandExecutionException("Alias does not exist.");

        sender.sendDetails(builder -> {
            builder.name("Alias").key(alias.getLabel());
            builder.item("Command", alias.getAlias());
            builder.item("Created", alias.getCreatedDate());
        });
    }

}
