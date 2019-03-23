package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentLabel;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentPage;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentString;
import com.github.manevolent.jbot.user.User;
import com.github.manevolent.jbot.user.UserGroup;
import com.github.manevolent.jbot.user.UserManager;

import java.util.Comparator;
import java.util.stream.Collectors;

public class GroupCommand extends AnnotatedCommandExecutor {
    private final UserManager userManager;

    public GroupCommand(UserManager userManager) {
        this.userManager = userManager;
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
