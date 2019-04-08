package io.manebot.command.builtin;

import io.manebot.chat.TextStyle;
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
import io.manebot.security.Grant;
import io.manebot.security.GrantedPermission;
import io.manebot.security.Permission;
import io.manebot.user.User;
import io.manebot.user.UserGroup;
import io.manebot.user.UserManager;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.function.Function;

public class PermissionCommand extends AnnotatedCommandExecutor {
    private final UserManager userManager;
    private final ConversationProvider conversationProvider;

    public PermissionCommand(UserManager userManager, ConversationProvider conversationProvider) {
        this.userManager = userManager;
        this.conversationProvider = conversationProvider;
    }

    @Override
    public String getDescription() {
        return "Manages permissions and security";
    }

    @Command(description = "Lists permissions of an entity", permission = "system.permission.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentSwitch.Argument(labels = {"user","group","conversation"}) String entityType,
                     @CommandArgumentString.Argument(label = "entity name") String entityName,
                     @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {
        Entity entity = createEntityAccessor(entityType).apply(entityName);

        sender.sendList(
                GrantedPermission.class,
                builder -> builder
                        .direct(new ArrayList<>(entity.getPermissions()))
                        .page(page)
                        .responder((textBuilder, permission) ->
                                textBuilder.append(permission.getGrant().name().toLowerCase()).append(" ")
                                        .append(permission.getPermission().toString(), EnumSet.of(TextStyle.BOLD))
                                        .append(" (granted by " + permission.getGranter().getDisplayName() +
                                        " on " + permission.getDate() + ")")
                        )
        );
    }

    @Command(description = "Adds permissions to an entity", permission = "system.permission.add")
    public void add(CommandSender sender,
                    @CommandArgumentLabel.Argument(label = "add") String list,
                    @CommandArgumentSwitch.Argument(labels = {"user","group","conversation"}) String entityType,
                    @CommandArgumentString.Argument(label = "entity name") String entityName,
                    @CommandArgumentString.Argument(label = "permission node") String node)
            throws CommandExecutionException {
        add(sender, "add", entityType, entityName, node, Grant.ALLOW.name().toLowerCase());
    }


    @Command(description = "Adds permissions to an entity", permission = "system.permission.add")
    public void add(CommandSender sender,
                    @CommandArgumentLabel.Argument(label = "add") String add,
                    @CommandArgumentSwitch.Argument(labels = {"user","group","conversation"}) String entityType,
                    @CommandArgumentString.Argument(label = "entity name") String entityName,
                    @CommandArgumentString.Argument(label = "permission node") String node,
                    @CommandArgumentSwitch.Argument(labels = {"allow", "deny"}) String grant)
            throws CommandExecutionException {
        Entity entity = createEntityAccessor(entityType).apply(entityName);
        if (entity.getPermission(node) != null)
            throw new CommandArgumentException("Permission already granted to entity.");
        node = node.toLowerCase();
        Grant g = grant.equalsIgnoreCase(Grant.ALLOW.name()) ? Grant.ALLOW : Grant.DENY;
        entity.setPermission(node, g);
        sender.sendMessage("Granted \"" + node + "\" to entity (" + g.name().toLowerCase() + ")");
    }


    @Command(description = "Removes permissions from an entity", permission = "system.permission.remove")
    public void remove(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "remove") String remove,
                       @CommandArgumentSwitch.Argument(labels = {"user","group","conversation"}) String entityType,
                       @CommandArgumentString.Argument(label = "entity name") String entityName,
                       @CommandArgumentString.Argument(label = "permission node") String node)
            throws CommandExecutionException {
        Permission.checkPermission(node);
        Entity entity = createEntityAccessor(entityType).apply(entityName);
        if (entity.getPermission(node) == null)
            throw new CommandArgumentException("Permission not granted to entity.");
        node = node.toLowerCase();
        entity.removePermission(node);
        sender.sendMessage("Removed \"" + node + "\" from entity.");
    }


    @Command(description = "Tests a permission on your user")
    public void test(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "test") String test,
                     @CommandArgumentString.Argument(label = "node") String node) {
        Permission.checkPermission(node);
        sender.sendMessage("Permission was allowed.");
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
