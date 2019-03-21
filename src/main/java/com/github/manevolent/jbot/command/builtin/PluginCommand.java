package com.github.manevolent.jbot.command.builtin;

import com.github.manevolent.jbot.artifact.ArtifactIdentifier;
import com.github.manevolent.jbot.command.CommandSender;
import com.github.manevolent.jbot.command.exception.CommandArgumentException;
import com.github.manevolent.jbot.command.exception.CommandExecutionException;
import com.github.manevolent.jbot.command.executor.chained.AnnotatedCommandExecutor;
import com.github.manevolent.jbot.command.executor.chained.argument.ChainedCommandArgumentLabel;

import com.github.manevolent.jbot.command.executor.chained.argument.ChainedCommandArgumentString;
import com.github.manevolent.jbot.plugin.Plugin;
import com.github.manevolent.jbot.plugin.PluginLoadException;
import com.github.manevolent.jbot.plugin.PluginManager;
import com.github.manevolent.jbot.plugin.PluginRegistration;

public class PluginCommand extends AnnotatedCommandExecutor {
    private final PluginManager pluginManager;

    public PluginCommand(PluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Command(description = "Lists plugins")
    public void list(CommandSender sender,
                     @ChainedCommandArgumentLabel.Argument(label = "list") String list) {

    }

    @Command(description = "Installs a plugin", permission = "system.plugin.install")
    public void install(CommandSender sender,
                        @ChainedCommandArgumentLabel.Argument(label = "install") String install,
                        @ChainedCommandArgumentString.Argument(label = "artifact") String artifact)
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
}
