package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentLabel;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentPage;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentString;
import com.github.manevolent.jbot.command.search.CommandArgumentSearch;
import com.github.manevolent.jbot.database.Database;
import com.github.manevolent.jbot.database.expressions.ExtendedExpressions;
import com.github.manevolent.jbot.database.expressions.MatchMode;
import com.github.manevolent.jbot.database.model.Group;
import com.github.manevolent.jbot.database.model.Platform;
import com.github.manevolent.jbot.database.search.*;
import com.github.manevolent.jbot.database.search.handler.*;
import com.github.manevolent.jbot.user.User;
import com.github.manevolent.jbot.user.UserGroup;
import com.github.manevolent.jbot.user.UserManager;

import javax.persistence.criteria.*;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.Collectors;

public class GroupCommand extends AnnotatedCommandExecutor {
    private final UserManager userManager;
    private final SearchHandler<Group> searchHandler;

    public GroupCommand(UserManager userManager, Database database) {
        this.userManager = userManager;

        this.searchHandler = database.createSearchHandler(Group.class)
                .string(new SearchHandlerPropertyContains("name"))
                .argument("owner", new ComparingSearchHandler(
                        new SearchHandlerPropertyEquals(root -> root.get("owningUser").get("displayName")),
                        new SearchHandlerPropertyEquals(root -> root.get("owningUser").get("username")),
                        SearchOperator.INCLUDE))
                .argument("member", new SearchHandlerPropertyIn("groupId",
                        root -> root.get("group").get("groupId"),
                        com.github.manevolent.jbot.database.model.UserGroup.class,
                        new ComparingSearchHandler(
                                new SearchHandlerPropertyEquals(root -> root.get("user").get("username")),
                                new SearchHandlerPropertyContains(root -> root.get("user").get("displayName")),
                                SearchOperator.INCLUDE
                        )
                ))
                .build();
    }

    @Command(description = "Searches groups", permission = "system.group.search")
    public void search(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "search") String search,
                       @CommandArgumentSearch.Argument Search query)
            throws CommandExecutionException {
        try {
            sender.list(
                    com.github.manevolent.jbot.database.model.Group.class,
                    searchHandler.search(query, 6),
                    (sender1, group) -> group.getName() + " (" + group.getUsers().size() +
                            " users, owned by " + group.getOwner().getDisplayName() + ")"
            ).send();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Command(description = "Lists groups", permission = "system.group.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {
        sender.list(
                UserGroup.class,
                builder -> builder.direct(
                        userManager.getUserGroups()
                                .stream()
                                .sorted(Comparator.comparing(UserGroup::getName))
                                .collect(Collectors.toList()))
                        .page(page)
                        .responder((sender1, group) -> group.getName() + " (" + group.getUsers().size() +
                                " users, owned by " + group.getOwner().getDisplayName() + ")")
                        .build()
        ).send();
    }

    @Command(description = "Creates a group", permission = "system.group.create")
    public void create(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "create") String create,
                       @CommandArgumentString.Argument(label = "name") String name)
            throws CommandExecutionException {
        UserGroup group = userManager.getUserGroupByName(name);
        if (group != null) throw new CommandArgumentException("Group already exists.");
        group = userManager.createUserGroup(name);
        sender.sendMessage("User group \"" + group.getName() + "\" created.");
    }

    @Command(description = "Gets group information", permission = "system.group.info")
    public void info(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "info") String info,
                     @CommandArgumentString.Argument(label = "name") String name)
            throws CommandExecutionException {
        UserGroup group = userManager.getUserGroupByName(name);
        if (group == null) throw new CommandArgumentException("Group does not exist.");

        sender.details(builder -> builder.name("Group").key(group.getName())
                .item("Owner", group.getOwner().getDisplayName())
                .item("Members", group.getUsers().stream().map(User::getDisplayName).collect(Collectors.toList()))
                .build()
        );
    }

    @Command(description = "Adds a user to a group", permission = "system.group.member.add")
    public void addUser(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "add") String add,
                        @CommandArgumentString.Argument(label = "username") String displayName,
                        @CommandArgumentString.Argument(label = "group name") String groupName)
            throws CommandExecutionException {
        User user = userManager.getUserByDisplayName(displayName);
        if (user == null) throw new CommandArgumentException("User does not exist.");

        UserGroup group = userManager.getUserGroupByName(groupName);
        if (group == null) throw new CommandArgumentException("Group does not exist.");

        if (group.isMember(user))
            throw new CommandArgumentException("User is already a member of this group.");

        group.addUser(user);

        sender.sendMessage("Added " + user.getDisplayName() + " to " + group.getName() + ".");
    }

    @Command(description = "Removes a user from a group", permission = "system.group.member.add")
    public void removeUser(CommandSender sender,
                           @CommandArgumentLabel.Argument(label = "remove") String remove,
                           @CommandArgumentString.Argument(label = "username") String displayName,
                           @CommandArgumentString.Argument(label = "group name") String groupName)
            throws CommandExecutionException {
        User user = userManager.getUserByDisplayName(displayName);
        if (user == null) throw new CommandArgumentException("User does not exist.");

        UserGroup group = userManager.getUserGroupByName(groupName);
        if (group == null) throw new CommandArgumentException("Group does not exist.");

        if (!group.isMember(user))
            throw new CommandArgumentException("User is not a member of this group.");

        group.removeUser(user);

        sender.sendMessage("Removed " + user.getDisplayName() + " from " + group.getName() + ".");
    }

    @Override
    public String getDescription() {
        return "Manages groups";
    }

}
