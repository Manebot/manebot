package io.manebot.command.builtin;

import io.manebot.chat.TextStyle;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandAccessException;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.command.executor.chained.argument.CommandArgumentSwitch;
import io.manebot.command.response.CommandListResponse;
import io.manebot.command.search.CommandArgumentSearch;
import io.manebot.database.Database;
import io.manebot.database.search.*;
import io.manebot.database.search.handler.*;
import io.manebot.platform.Platform;
import io.manebot.platform.PlatformManager;
import io.manebot.user.*;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.Date;
import java.util.EnumSet;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class UserCommand extends AnnotatedCommandExecutor {
    private final PlatformManager platformManager;
    private final UserManager userManager;
    private final Database database;

    private final SearchHandler<io.manebot.database.model.User> searchHandler;

    public UserCommand(PlatformManager platformManager, UserManager userManager, Database database) {
        this.platformManager = platformManager;
        this.userManager = userManager;
        this.database = database;

        this.searchHandler = database
                .createSearchHandler(io.manebot.database.model.User.class)
                .string(new ComparingSearchHandler(
                            new SearchHandlerPropertyEquals("username"),
                            new SearchHandlerPropertyContains("displayName"),
                            SearchOperator.INCLUDE))
                .command("unnamed", new SearchHandlerPropertyIsNull("displayName"))
                .argument("group", new SearchHandlerPropertyIn("userId",
                        root -> root.get("user").get("userId"),
                        io.manebot.database.model.UserGroup.class,
                        new SearchHandlerPropertyEquals(root -> root.get("group").get("name"))
                ))
                .build();
    }

    @Command(description = "Changes a user's type", permission = "system.user.type.set")
    public void setType(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "type") String typeLabel,
                        @CommandArgumentLabel.Argument(label = "set") String setLabel,
                        @CommandArgumentString.Argument(label = "username") String displayName,
                        @CommandArgumentSwitch.Argument(labels = {"admin","standard","anonymous"}) String type)
            throws CommandExecutionException {
        if (sender.getUser().getType() != UserType.SYSTEM)
            throw new CommandAccessException("Only system users can change another user's type.");

        User user = userManager.getUserByDisplayName(displayName);
        if (user == null)
            throw new CommandArgumentException("User not found.");

        if (user == sender.getUser())
            throw new CommandAccessException("Cannot change your own user's type.");

        UserType userType;

        switch (type) {
            case "admin":
                userType = UserType.SYSTEM;
                break;
            case "standard":
                userType = UserType.COMMON;
                break;
            case "anonymous":
                userType = UserType.ANONYMOUS;
                break;
            default:
                throw new CommandArgumentException("Unrecognized user type");
        }

        user.setType(userType);

        sender.sendMessage("User type has been updated.");
    }

    @Command(description = "Searches users", permission = "system.user.search")
    public void search(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "search") String search,
                     @CommandArgumentSearch.Argument Search query)
            throws CommandExecutionException {
        try {
            sender.sendList(
                    io.manebot.database.model.User.class,
                    searchHandler.search(query, 6),
                    (textBuilder, user) -> textBuilder.append(user.getDisplayName())
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Command(description = "Lists users", permission = "system.user.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {
        sender.sendList(
                User.class,
                builder -> builder.direct(
                        userManager.getUsers()
                        .stream()
                        .sorted(Comparator.comparing(User::getDisplayName))
                        .collect(Collectors.toList()))
                .page(page)
                .responder((textBuilder, user) -> textBuilder.append(user.getDisplayName()))
        );
    }

    @Command(description = "Creates a user", permission = "system.user.create")
    public void create(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "create") String create,
                     @CommandArgumentString.Argument(label = "username") String username,
                     @CommandArgumentSwitch.Argument(labels = {"admin","standard","anonymous"}) String type)
            throws CommandExecutionException {
        UserType userType;

        switch (type) {
            case "admin":
                userType = UserType.SYSTEM;
                break;
            case "standard":
                userType = UserType.COMMON;
                break;
            case "anonymous":
                userType = UserType.ANONYMOUS;
                break;
            default:
                throw new CommandArgumentException("Unrecognized user type");
        }

        User user = userManager.createUser(username, userType);

        sender.sendMessage("Created user " + user.getUsername() + ".");
    }

    @Command(description = "Creates a standard user", permission = "system.user.create")
    public void create(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "create") String create,
                     @CommandArgumentString.Argument(label = "username") String username)
            throws CommandExecutionException {
        create(sender, create, username, "standard");
    }

    @Command(description = "Gets user information", permission = "system.user.info")
    public void info(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "info") String info,
                       @CommandArgumentString.Argument(label = "display name") String displayName)
            throws CommandExecutionException {
        User user = userManager.getUserByDisplayName(displayName);
        if (user == null)
            throw new CommandArgumentException("User not found.");

        String exposedUsername = displayName.equalsIgnoreCase(user.getDisplayName()) ?
                user.getDisplayName() :
                user.getName();

        sender.sendDetails(builder -> {
            builder.name("User").key(exposedUsername);

            Date lastSeen = user.getLastSeenDate();
            if (lastSeen != null)
                builder.item("Last seen", lastSeen.toString());
            else
                builder.item("Last seen", "(never)");

            builder.item("Registered", user.getRegisteredDate().toString());
            builder.item("Groups", user.getGroups().stream().map(UserGroup::getName).collect(Collectors.toList()));
        });
    }

    @Command(description = "Gets user connections", permission = "system.user.connection.list")
    public void listConnections(CommandSender sender,
                                @CommandArgumentLabel.Argument(label = "connection") String links,
                                @CommandArgumentLabel.Argument(label = "list") String list,
                                @CommandArgumentString.Argument(label = "display name") String displayName,
                                @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        User user = userManager.getUserByDisplayName(displayName);
        if (user == null)
            throw new CommandArgumentException("User not found.");

        sender.sendList(
                UserAssociation.class,
                builder -> builder.direct(user.getAssociations()
                        .stream()
                        .sorted(
                                Comparator.comparing((Function<UserAssociation, String>)
                                        userAssociation -> userAssociation.getPlatform().getId()
                                ).thenComparing(UserAssociation::getPlatformId))
                        .collect(Collectors.toList()))
                .page(page)
                .responder((textBuilder, assoc) ->
                        textBuilder.append(assoc.getPlatform().getId(), EnumSet.of(TextStyle.BOLD))
                                .append(": ")
                                .append(assoc.getPlatformId()))
        );
    }

    @Command(description = "Creates a user connection", permission = "system.user.connection.create")
    public void createConnection(CommandSender sender,
                                 @CommandArgumentLabel.Argument(label = "connection") String connection,
                                 @CommandArgumentLabel.Argument(label = "create") String create,
                                 @CommandArgumentString.Argument(label = "display name") String displayName,
                                 @CommandArgumentString.Argument(label = "platform") String platformId,
                                 @CommandArgumentString.Argument(label = "id") String userId)
            throws CommandExecutionException {
        User user = userManager.getUserByDisplayName(displayName);
        if (user == null)
            throw new CommandArgumentException("User not found.");

        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null)
            throw new CommandArgumentException("Platform not found.");

        if (user.getUserAssociation(platform, userId) != null)
            throw new CommandArgumentException("This association already exists.");

        UserAssociation association = user.createAssociation(platform, userId);

        sender.sendMessage("Created association: " +
                association.getPlatform().getId() + " <-> " + association.getPlatformId());
    }

    @Command(description = "Removes a user connection", permission = "system.user.connection.remove")
    public void removeConnection(CommandSender sender,
                                 @CommandArgumentLabel.Argument(label = "connection") String connection,
                                 @CommandArgumentLabel.Argument(label = "remove") String create,
                                 @CommandArgumentString.Argument(label = "platform") String platformId,
                                 @CommandArgumentString.Argument(label = "id") String userId)
            throws CommandExecutionException {
        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null)
            throw new CommandArgumentException("Platform not found.");

        UserAssociation association = platform.getUserAssocation(userId);
        if (association == null)
            throw new CommandArgumentException("This association doesn't exist.");
        else if (association == sender.getPlatformUser().getAssociation())
            throw new CommandArgumentException("Cannot remove own association.");

        association.remove();

        sender.sendMessage("Removed association: " +
                association.getPlatform().getId() + " <-> " + association.getPlatformId());
    }

    @Override
    public String getDescription() {
        return "Manages users";
    }

}
