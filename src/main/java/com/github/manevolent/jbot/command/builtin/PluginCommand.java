package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.artifact.ArtifactIdentifier;
import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentLabel;

import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentPage;
import com.github.manevolent.jbot.command.executor.chained.argument.CommandArgumentString;
import com.github.manevolent.jbot.command.response.CommandListResponse;
import com.github.manevolent.jbot.database.Database;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.plugin.*;

import java.util.Comparator;
import java.util.stream.Collectors;

public class PluginCommand extends AnnotatedCommandExecutor {
    private final PluginManager pluginManager;

    public PluginCommand(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Command(description = "Lists plugins", permission = "system.plugin.list")
    public void list(CommandSender sender,
                     @CommandArgumentLabel.Argument(label = "list") String list,
                     @CommandArgumentPage.Argument() int page) throws CommandExecutionException {
        sender.list(
                Plugin.class,
                builder -> builder.direct(pluginManager.getLoadedPlugins()
                        .stream()
                        .sorted(Comparator.comparing(Plugin::getName))
                        .collect(Collectors.toList()))
                .page(page)
                .responder((sender1, plugin) -> plugin.getName() + " (" + plugin.getArtifact().getIdentifier() + ")")
                .build()
        ).send();
    }

    @Command(description = "Installs a plugin", permission = "system.plugin.install")
    public void install(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "install") String install,
                        @CommandArgumentString.Argument(label = "artifact") String artifact)
            throws CommandExecutionException {
        ArtifactIdentifier artifactIdentifier = pluginManager.resolveIdentifier(artifact);
        if (artifactIdentifier == null)
            throw new CommandArgumentException("Plugin not found, or no versions are available.");

        PluginRegistration registration = pluginManager.getPlugin(artifactIdentifier.withoutVersion());
        if (registration != null)
            throw new CommandArgumentException(
                    artifactIdentifier.withoutVersion().toString()
                    + " is already installed with version " + registration.getIdentifier().getVersion() + "."
            );

        sender.sendMessage("Installing plugin " + artifactIdentifier.toString() + "...");
        sender.flush();

        try {
            registration = pluginManager.install(artifactIdentifier);
        } catch (PluginLoadException e) {
            throw new CommandExecutionException("Failed to install " + artifactIdentifier.toString(), e);
        }

        sender.sendMessage("Installed plugin " + registration.getIdentifier().toString() + ".");
    }

    @Command(description = "Uninstalls a plugin", permission = "system.plugin.uninstall")
    public void uninstall(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "uninstall") String uninstall,
                        @CommandArgumentString.Argument(label = "artifact") String artifact)
            throws CommandExecutionException {
        ArtifactIdentifier artifactIdentifier = pluginManager.resolveIdentifier(artifact);
        if (artifactIdentifier == null)
            throw new CommandArgumentException("Plugin not found, or no versions are available.");

        PluginRegistration registration = pluginManager.getPlugin(artifactIdentifier.withoutVersion());
        if (registration == null)
            throw new CommandArgumentException(artifactIdentifier.withoutVersion().toString() + " is not installed.");

        sender.sendMessage("Uninstalling plugin " + artifactIdentifier.toString() + "...");
        sender.flush();

        if (registration.isLoaded()) {
            try {
                registration.getInstance().setEnabled(false);
            } catch (PluginException e) {
                throw new CommandExecutionException("Failed to disable " + artifactIdentifier.toString(), e);
            }
        }

        pluginManager.uninstall(registration);

        sender.sendMessage("Uninstalled plugin " + registration.getIdentifier().toString() + ".");
    }

    @Command(description = "Enables a plugin", permission = "system.plugin.enable")
    public void enable(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "enable") String enable,
                        @CommandArgumentString.Argument(label = "identifier") String identifier)
            throws CommandExecutionException {
        ArtifactIdentifier artifactIdentifier = pluginManager.resolveIdentifier(identifier);
        if (artifactIdentifier == null)
            throw new CommandArgumentException("Plugin not found, or no versions are available.");

        PluginRegistration registration = pluginManager.getPlugin(artifactIdentifier.withoutVersion());
        if (registration == null)
            throw new CommandArgumentException(artifactIdentifier.withoutVersion().toString() + " is not installed.");

        sender.sendMessage("Enabling plugin " + artifactIdentifier.toString() + "...");
        sender.flush();

        try {
            if (!registration.isLoaded())
                registration.load();

            registration.getInstance().setEnabled(true);
        } catch (PluginException e) {
            throw new CommandExecutionException("Failed to enable " + artifactIdentifier.toString(), e);
        }

        sender.sendMessage("Enabled plugin " + registration.getIdentifier().toString() + ".");
    }

    @Command(description = "Gets plugin information", permission = "system.plugin.info")
    public void info(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "info") String info,
                       @CommandArgumentString.Argument(label = "identifier") String identifier)
            throws CommandExecutionException {
        ArtifactIdentifier artifactIdentifier = pluginManager.resolveIdentifier(identifier);
        if (artifactIdentifier == null)
            throw new CommandArgumentException("Plugin not found, or no versions are available.");

        PluginRegistration registration = pluginManager.getPlugin(artifactIdentifier.withoutVersion());
        if (registration == null)
            throw new CommandArgumentException(artifactIdentifier.withoutVersion().toString() + " is not installed.");

        sender.details(builder -> {
            builder.name("Plugin").key(registration.getIdentifier().withoutVersion().toString())
                    .item("Version", registration.getIdentifier().getVersion());

            if (registration.isLoaded()) {
                builder.item("Loaded", "true");

                builder.item("Dependencies", registration.getInstance().getDependencies()
                                .stream().map(Plugin::getName).collect(Collectors.toList()));

                builder.item("Databases", registration.getInstance().getDatabases()
                        .stream().map(Database::getName).collect(Collectors.toList()));

                if (registration.getInstance().isEnabled()) {
                    builder.item("Enabled", "true");

                    builder.item("Platforms", registration.getInstance().getPlatforms()
                            .stream().map(Platform::getId).collect(Collectors.toList()));

                    builder.item("Commands", registration.getInstance().getCommands());
                } else {
                    builder.item("Enabled", "false");
                }
            } else
                builder.item("Loaded", "false");

            return builder.build();
        }).send();
    }

    @Command(description = "Disables a plugin", permission = "system.plugin.disable")
    public void disable(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "disable") String disable,
                       @CommandArgumentString.Argument(label = "identifier") String identifier)
            throws CommandExecutionException {
        ArtifactIdentifier artifactIdentifier = pluginManager.resolveIdentifier(identifier);
        if (artifactIdentifier == null)
            throw new CommandArgumentException("Plugin not found, or no versions are available.");

        PluginRegistration registration = pluginManager.getPlugin(artifactIdentifier.withoutVersion());
        if (registration == null)
            throw new CommandArgumentException(artifactIdentifier.withoutVersion().toString() + " is not installed.");

        sender.sendMessage("Disabling plugin " + artifactIdentifier.toString() + "...");
        sender.flush();

        try {
            if (registration.isLoaded())
                registration.getInstance().setEnabled(false);
        } catch (PluginException e) {
            throw new CommandExecutionException("Failed to disable " + artifactIdentifier.toString(), e);
        }

        sender.sendMessage("Disabled plugin " + registration.getIdentifier().toString() + ".");
    }

    @Override
    public String getDescription() {
        return "Manages plugins";
    }
}
