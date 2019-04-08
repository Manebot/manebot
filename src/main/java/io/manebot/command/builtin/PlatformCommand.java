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
import io.manebot.command.response.CommandListResponse;
import io.manebot.command.search.CommandArgumentSearch;
import io.manebot.database.Database;
import io.manebot.database.search.Search;
import io.manebot.database.search.SearchHandler;
import io.manebot.database.search.handler.SearchHandlerPropertyContains;
import io.manebot.platform.Platform;
import io.manebot.platform.PlatformManager;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginException;
import io.manebot.user.UserGroup;
import io.manebot.user.UserManager;

import java.sql.SQLException;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.stream.Collectors;

public class PlatformCommand extends AnnotatedCommandExecutor {
    private final UserManager userManager;
    private final PlatformManager platformManager;
    private final SearchHandler<io.manebot.database.model.Platform> searchHandler;

    private final CommandListResponse.ListElementFormatter<io.manebot.database.model.Platform> modelFormatter =
            (textBuilder, platform) ->
            textBuilder.append(platform.getId(), EnumSet.of(TextStyle.BOLD))
                    .append(" ")
                    .append(
                            platform.isConnected() ? "(connected)" : "(disconnected)",
                            EnumSet.of(TextStyle.ITALICS)
                    );

    private final CommandListResponse.ListElementFormatter<Platform> abstractFormatter =
            (textBuilder, platform) ->
                    textBuilder.append(platform.getId(), EnumSet.of(TextStyle.BOLD))
                            .append(" ")
                            .append(
                                    platform.isConnected() ? "(connected)" : "(disconnected)",
                                    EnumSet.of(TextStyle.ITALICS)
                            );

    public PlatformCommand(UserManager userManager, PlatformManager platformManager, Database database) {
        this.userManager = userManager;
        this.platformManager = platformManager;
        this.searchHandler = database
                .createSearchHandler(io.manebot.database.model.Platform.class)
                .string(new SearchHandlerPropertyContains("id"))
                .build();
    }

    @Command(description = "Searches platforms", permission = "system.platform.search")
    public void search(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "search") String search,
                       @CommandArgumentSearch.Argument Search query)
            throws CommandExecutionException {
        try {
            sender.sendList(
                    io.manebot.database.model.Platform.class,
                    searchHandler.search(query, 6),
                    modelFormatter
            );
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Command(description = "Unsets the new user group for a platform", permission = "system.platform.defaultgroup.set")
    public void unsetDefaultGroup(CommandSender sender,
                                @CommandArgumentLabel.Argument(label = "unset") String set,
                                @CommandArgumentLabel.Argument(label = "newusergroup") String newusergroup,
                                @CommandArgumentString.Argument(label = "platform id") String platformId)
            throws CommandExecutionException {
        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null) throw new CommandArgumentException("Platform not found.");

        platform.setDefaultGroup(null);

        sender.sendMessage("New user registrations for " + platform.getId() + " will not be added to any group.");
    }

    @Command(description = "Sets the new user group for a platform", permission = "system.platform.defaultgroup.set")
    public void setDefaultGroup(CommandSender sender,
                                @CommandArgumentLabel.Argument(label = "set") String set,
                                @CommandArgumentLabel.Argument(label = "newusergroup") String newusergroup,
                                @CommandArgumentString.Argument(label = "platform id") String platformId,
                                @CommandArgumentString.Argument(label = "group name") String groupName)
            throws CommandExecutionException {
        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null) throw new CommandArgumentException("Platform not found.");

        UserGroup userGroup = userManager.getUserGroupByName(groupName);
        if (userGroup == null) throw new CommandArgumentException("Group not found.");

        platform.setDefaultGroup(userGroup);

        sender.sendMessage(
                "New user registrations for " + platform.getId() +
                " will be added to " + userGroup.getName() + "."
        );
    }

    @Command(description = "Sets the registration behavior for a platform", permission = "system.platform.registration.set")
    public void setRegistration(CommandSender sender,
                                @CommandArgumentLabel.Argument(label = "set") String set,
                                @CommandArgumentLabel.Argument(label = "registration") String registratiopn,
                                @CommandArgumentString.Argument(label = "platform id") String platformId,
                                @CommandArgumentSwitch.Argument(labels = {"enable","disable"}) String switchValue)
            throws CommandExecutionException {
        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null) throw new CommandArgumentException("Platform not found.");

        boolean allowed = switchValue.equalsIgnoreCase("enable");

        platform.setRegistrationAllowed(allowed);

        if (allowed) {
            sender.sendMessage("User registration enabled for " + platform.getId() + ".");
        } else {
            sender.sendMessage("User registration disabled for " + platform.getId() + ".");
        }
    }

    @Command(description = "Connects a platform", permission = "system.platform.connect")
    public void connect(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "connect") String connect,
                        @CommandArgumentString.Argument(label = "platform id") String platformId)
            throws CommandExecutionException {
        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null)
            throw new CommandArgumentException("Platform not found.");

        if (platform.isConnected())
            throw new CommandArgumentException("Platform is already connected.");

        if (platform.getConnection() == null)
            throw new CommandArgumentException("Platform is not registered.");

        try {
            platform.getConnection().connect();
        } catch (PluginException e) {
            throw new CommandExecutionException("Failed to connect to platform " + platform.getId(), e);
        }

        if (platform.getConnection().isConnected())
            sender.sendMessage("Platform connected successfully.");
        else
            throw new CommandExecutionException("Platform did not connect after attempting to make a connection.");
    }

    @Command(description = "Disconnects a platform", permission = "system.platform.disconnect")
    public void disconnct(CommandSender sender,
                          @CommandArgumentLabel.Argument(label = "disconnect") String disconnect,
                          @CommandArgumentString.Argument(label = "platform id") String platformId)
            throws CommandExecutionException {
        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null)
            throw new CommandArgumentException("Platform not found.");

        if (!platform.isConnected())
            throw new CommandArgumentException("Platform is already disconnected.");

        if (platform.getConnection() == null)
            throw new CommandArgumentException("Platform is not registered.");

        try {
            platform.getConnection().disconnect();
        } catch (UnsupportedOperationException ex) {
            throw new CommandArgumentException("Platform does not support disconnecting.");
        } catch (PluginException e) {
            throw new CommandExecutionException("Failed to disconnect platform " + platform.getId(), e);
        }

        if (!platform.getConnection().isConnected())
            sender.sendMessage("Platform disconnected successfully.");
        else
            throw new CommandExecutionException(
                    "Platform did not disconnect after " +
                            "attempting to close its connection."
            );
    }

    @Command(description = "Lists platforms", permission = "system.platform.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentPage.Argument int page)
            throws CommandExecutionException {
        sender.sendList(
                Platform.class,
                builder -> builder
                        .direct(platformManager.getPlatforms()
                                .stream()
                                .sorted(Comparator.comparing(Platform::getId))
                                .collect(Collectors.toList()))
                        .page(page)
                        .responder(abstractFormatter)
        );
    }

    @Command(description = "Gets platform information", permission = "system.platform.info")
    public void info(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "info") String info,
                     @CommandArgumentString.Argument(label = "platform id") String platformId)
            throws CommandExecutionException {
        Platform platform = platformManager.getPlatformById(platformId);
        if (platform == null)
            throw new CommandArgumentException("Platform not found.");

        sender.sendDetails(builder -> {
            builder.name("Platform").key(platform.getId());

            Plugin plugin = platform.getPlugin();
            if (plugin != null) {
                builder.item("Plugin", plugin.getArtifact().getIdentifier().toString());
            } else {
                builder.item("Plugin", "(none)");
            }

            if (platform.getRegistration() != null) {
                builder.item("Registered", "true");
                builder.item("Connected", Boolean.toString(platform.isConnected()));
            } else {
                builder.item("Registered", "false");
            }

            builder.item("User registration", platform.isRegistrationAllowed() ? "enabled" : "disabled");

            UserGroup group = platform.getDefaultGroup();
            if (group != null) {
                builder.item("New user group", group.getName());
            } else {
                builder.item("New user group", "(none)");
            }
        });
    }

    @Override
    public String getDescription() {
        return "Manages platforms";
    }

}
