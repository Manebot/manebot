package io.manebot.command.builtin;

import io.manebot.chat.TextStyle;
import io.manebot.command.CommandSender;
import io.manebot.command.exception.CommandArgumentException;
import io.manebot.command.exception.CommandExecutionException;
import io.manebot.command.executor.chained.AnnotatedCommandExecutor;
import io.manebot.command.executor.chained.argument.CommandArgumentLabel;
import io.manebot.command.executor.chained.argument.CommandArgumentPage;
import io.manebot.command.executor.chained.argument.CommandArgumentString;
import io.manebot.database.Database;
import io.manebot.database.model.Repository;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.net.MalformedURLException;
import java.net.URI;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;

public class RepositoryCommand extends AnnotatedCommandExecutor {
    private final Database database;

    public RepositoryCommand(Database systemDatabase) {
        this.database = systemDatabase;
    }

    @Command(description = "Adds a repository", permission = "system.repository.add")
    public void add(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "add") String add,
                        @CommandArgumentString.Argument(label = "id") String id,
                        @CommandArgumentString.Argument(label = "url") String url)
            throws CommandExecutionException, SQLException {
        add(sender, add, id, "default", url);
    }

    @Command(description = "Adds a repository", permission = "system.repository.add")
    public void add(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "add") String add,
                        @CommandArgumentString.Argument(label = "id") String id,
                        @CommandArgumentString.Argument(label = "type") String type,
                        @CommandArgumentString.Argument(label = "url") String url)
            throws CommandExecutionException, SQLException {
        try {
            URI.create(url).toURL();
        } catch (MalformedURLException e) {
            throw new CommandArgumentException("Invalid URL");
        }

        Repository repo = database.executeTransaction(s -> {
            Repository repository = new Repository(database, id, type, url);
            s.persist(repository);
            return repository;
        });

        sender.sendMessage("Created repository \"" + repo.getId() + "\".");
    }

    @Command(description = "Removes a repository", permission = "system.repository.remove")
    public void remove(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "remove") String remove,
                        @CommandArgumentString.Argument(label = "id") String id)
            throws CommandArgumentException {
        Repository repo = database.execute(s -> {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<Repository> criteriaQuery = cb.createQuery(Repository.class);
            Root<Repository> from = criteriaQuery.from(Repository.class);
            criteriaQuery.select(from);
            criteriaQuery.where(cb.equal(from.get("id"), id));
            return s.createQuery(criteriaQuery).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        });

        if (repo == null) throw new CommandArgumentException("Repository not found");

        repo.remove();

        sender.sendMessage("Removed repository \"" + repo.getId() + "\".");
    }

    @Command(description = "Lists repositories", permission = "system.repository.list")
    public void list(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "list") String list,
                        @CommandArgumentPage.Argument() int page)
            throws CommandExecutionException {
        Collection<Repository> repo = database.execute(s -> {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<Repository> criteriaQuery = cb.createQuery(Repository.class);
            Root<Repository> from = criteriaQuery.from(Repository.class);
            criteriaQuery.select(from);
            return new ArrayList<>(s.createQuery(criteriaQuery).getResultList());
        });

        sender.list(
                Repository.class,
                builder -> builder
                        .direct(new ArrayList<>(repo))
                        .page(page)
                        .responder((textBuilder, repo1) -> textBuilder
                                .append(repo1.getId(), EnumSet.of(TextStyle.BOLD))
                                .append(": ")
                                .append(repo1.getUrl()))
                        .build()
        ).send();
    }
}
