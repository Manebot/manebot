package io.manebot.command.alias;

import io.manebot.command.CommandManager;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.CommandExecutor;
import io.manebot.database.model.CommandAlias;

import java.util.Collections;
import java.util.List;

public class AliasedCommandExecutor implements CommandExecutor {
    private final CommandManager commandManager;
    private final CommandAlias alias;

    public AliasedCommandExecutor(CommandManager commandManager, CommandAlias alias) {
        this.commandManager = commandManager;
        this.alias = alias;
    }

    private String[] getTargetArguments() {
        String[] path = alias.getAlias().split(" ");
        if (path.length <= 1) return new String[0];
        String[] args = new String[path.length - 1];
        System.arraycopy(path, 1, args, 0, args.length);
        return args;
    }

    private String[] buildArguments(String[] append) {
        String[] target = getTargetArguments();
        if (target.length > 0) {
            String[] appended = new String[target.length + append.length];
            System.arraycopy(target, 0, appended, 0, target.length);
            System.arraycopy(append, 0, appended, target.length, append.length);
            return appended;
        } else return append;
    }

    private String getTargetLabel() {
        return alias.getAlias().split(" ")[0].toLowerCase();
    }

    private CommandExecutor getTargetCommand() {
        return commandManager.getExecutor(getTargetLabel());
    }

    @Override
    public void execute(CommandSender sender, String label, String[] args) throws CommandExecutionException {
        CommandExecutor executor = getTargetCommand();
        if (executor == null)
            throw new CommandArgumentException("Alias target \"" + getTargetLabel() + "\" was not found.");

        executor.execute(sender, label, buildArguments(args));
    }

    @Override
    public String getDescription() {
        CommandExecutor executor = getTargetCommand();
        if (executor != null)
            return "Alias of " + getTargetLabel() + ": " + getTargetCommand().getDescription();
        else
            return "Alias of " + getTargetLabel();
    }

    @Override
    public List<String> getHelp(CommandSender sender, String label, String[] args) throws CommandExecutionException {
        CommandExecutor executor = getTargetCommand();
        if (executor != null)
            return executor.getHelp(sender, label, buildArguments(args));
        else
            return Collections.emptyList();
    }

    @Override
    public boolean isBuffered() {
        CommandExecutor executor = getTargetCommand();
        return executor == null || executor.isBuffered();
    }
}
