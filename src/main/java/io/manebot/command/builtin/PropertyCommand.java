package io.manebot.command.builtin;

import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.command.executor.chained.argument.CommandArgumentSwitch;
import io.manebot.conversation.Conversation;
import io.manebot.conversation.ConversationProvider;
import io.manebot.entity.Entity;
import io.manebot.property.Property;
import io.manebot.user.User;
import io.manebot.user.UserGroup;
import io.manebot.user.UserManager;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

public class PropertyCommand extends AnnotatedCommandExecutor {
    private final UserManager userManager;
    private final ConversationProvider conversationProvider;

    public PropertyCommand(UserManager userManager, ConversationProvider conversationProvider) {
        this.userManager = userManager;
        this.conversationProvider = conversationProvider;
    }

    @Override
    public String getDescription() {
        return "Manages entity properties";
    }

    @Command(description = "Lists properties of an entity", permission = "system.user.property.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentSwitch.Argument(labels = {"user","group","conversation"}) String entityType,
                     @CommandArgumentString.Argument(label = "entity name") String entityName,
                     @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {
        Function<String, Entity>  entityFunction = createEntityAccessor(entityType);

        Entity entity = entityFunction.apply(entityName);

        sender.list(
                Property.class,
                builder -> builder
                        .direct(new ArrayList<>(
                                entity.getProperties()
                                .stream().sorted(Comparator.comparing(Property::getName))
                                        .collect(Collectors.toList())
                        ))
                        .page(page)
                        .responder((sender1, property) -> property.getName())
                        .build()
        ).send();
    }

    @Command(description = "Sets property on an entity", permission = "system.user.property.set")
    public void set(CommandSender sender,
                    @CommandArgumentLabel.Argument(label = "set") String set,
                    @CommandArgumentSwitch.Argument(labels = {"user","group","conversation"}) String entityType,
                    @CommandArgumentString.Argument(label = "entity name") String entityName,
                    @CommandArgumentString.Argument(label = "name") String name,
                    @CommandArgumentString.Argument(label = "base64") String base64)
            throws CommandExecutionException {
        Function<String, Entity>  entityFunction = createEntityAccessor(entityType);
        Entity entity = entityFunction.apply(entityName);
        entity.getPropery(name).set(Base64.getDecoder().decode(base64));
        sender.sendMessage("Property set.");
    }


    @Command(description = "Gets property for an entity", permission = "system.user.property.get")
    public void remove(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "get") String get,
                       @CommandArgumentSwitch.Argument(labels = {"user","group","conversation"}) String entityType,
                       @CommandArgumentString.Argument(label = "entity name") String entityName,
                       @CommandArgumentString.Argument(label = "name") String name)
            throws CommandExecutionException {
        Function<String, Entity> entityFunction = createEntityAccessor(entityType);
        Entity entity = entityFunction.apply(entityName);
        Property property = entity.getPropery(name);
        if (property.isNull())
            sender.sendMessage(property.getName() + " => (null)");
        else
            sender.sendMessage(property.getName() + " => " + Base64.getEncoder().encodeToString(property.getBytes()));
    }

    @Command(description = "Unsets property on an entity", permission = "system.user.property.set")
    public void unset(CommandSender sender,
                    @CommandArgumentLabel.Argument(label = "unset") String unset,
                    @CommandArgumentSwitch.Argument(labels = {"user","group","conversation"}) String entityType,
                    @CommandArgumentString.Argument(label = "entity name") String entityName,
                    @CommandArgumentString.Argument(label = "name") String name)
            throws CommandExecutionException {
        Function<String, Entity>  entityFunction = createEntityAccessor(entityType);
        Entity entity = entityFunction.apply(entityName);
        Property property = entity.getPropery(name);
        if (property.isNull())
            throw new CommandArgumentException("Property is already null");
        else {
            property.unset();
            sender.sendMessage(property.getName() + " unset.");
        }
    }

    private Function<String, Entity> createEntityAccessor(String type) {
        switch (type.toLowerCase()) {
            case "user":
                return entityName -> {
                    User user = userManager.getUserByDisplayName(entityName);
                    if (user == null) throw new IllegalArgumentException("User not found.");
                    return user.getEntity();
                };
            case "group":
                return entityName -> {
                    UserGroup group = userManager.getUserGroupByName(entityName);
                    if (group == null) throw new IllegalArgumentException("User group not found.");
                    return group.getEntity();
                };
            case "conversation":
                return entityName -> {
                    Conversation conversation = conversationProvider.getConversationById(entityName);
                    if (conversation == null) throw new IllegalArgumentException("Conversation not found.");
                    return conversation.getEntity();
                };
            default:
                throw new IllegalArgumentException("Unknown entity type");
        }
    }

}
