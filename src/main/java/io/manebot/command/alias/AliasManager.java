package io.manebot.command.alias;

import io.manebot.command.CommandManager;
import io.manebot.command.executor.CommandExecutor;
import io.manebot.database.Database;
import io.manebot.database.model.CommandAlias;

import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AliasManager {
    private final Database database;
    private final CommandManager commandManager;
    private final Map<String, CommandExecutor> registeredExecutors = new LinkedHashMap<>();

    public AliasManager(Database database, CommandManager commandManager) {
        this.database = database;
        this.commandManager = commandManager;
    }

    public List<CommandAlias> getAliases() {
        return database.execute(s -> {
            return s.createQuery("SELECT x FROM " + CommandAlias.class.getName() + " x", CommandAlias.class)
                    .getResultList();
        });
    }

    public CommandAlias createAlias(String label, String alias) {
        try {
            return database.executeTransaction(s -> {
                CommandAlias newAlias = new CommandAlias(database, label, alias);
                s.persist(newAlias);
                return newAlias;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public CommandAlias getAlias(String label) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + CommandAlias.class.getName() + " x " +
                    "WHERE x.label = :label",
                    CommandAlias.class)
                    .setParameter("label", label)
                    .setMaxResults(1)
                    .getResultStream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public void unregisterAliases() {
        Iterator<Map.Entry<String, CommandExecutor>> it = registeredExecutors.entrySet().iterator();
        while (it.hasNext()) {
            commandManager.unregisterExecutor(it.next().getKey());
            it.remove();
        }
    }

    public void registerAliases() {
        for (CommandAlias alias : getAliases()) {
            CommandExecutor executor;

            try {
                commandManager.registerExecutor(
                        alias.getLabel(),
                        executor = new AliasedCommandExecutor(commandManager, alias)
                );
            } catch (Exception ex) {
                Logger.getGlobal().log(Level.WARNING, "Failed to register alias \"" + alias.getLabel() + "\"", ex);
                continue;
            }

            registeredExecutors.put(alias.getLabel(), executor);
        }
    }

    public void reregisterAliases() {
        unregisterAliases();
        registerAliases();
    }
}
