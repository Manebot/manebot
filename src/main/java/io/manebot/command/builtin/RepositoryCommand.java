package io.manebot.command.builtin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import io.manebot.artifact.Repositories;
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
import org.eclipse.aether.repository.RemoteRepository;

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

    @Command(description = "Disables a repository", permission = "system.repository.disable")
    public void disable(CommandSender sender,
                    @CommandArgumentLabel.Argument(label = "disable") String disable,
                    @CommandArgumentString.Argument(label = "id") String id)
            throws CommandExecutionException, SQLException {
        Repository repo = database.execute(s -> {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<Repository> criteriaQuery = cb.createQuery(Repository.class);
            Root<Repository> from = criteriaQuery.from(Repository.class);
            criteriaQuery.select(from);
            criteriaQuery.where(cb.equal(from.get("id"), id));
            return s.createQuery(criteriaQuery).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        });

        if (!repo.isEnabled())
            throw new CommandArgumentException("Repository is already disabled.");

        repo.setEnabled(false);

        sender.sendMessage("Disabled repository \"" + repo.getId() + "\".");
    }

    @Command(description = "Enables a repository", permission = "system.repository.enable")
    public void enable(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "enable") String enable,
                        @CommandArgumentString.Argument(label = "id") String id)
            throws CommandExecutionException, SQLException {
        Repository repo = database.execute(s -> {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<Repository> criteriaQuery = cb.createQuery(Repository.class);
            Root<Repository> from = criteriaQuery.from(Repository.class);
            criteriaQuery.select(from);
            criteriaQuery.where(cb.equal(from.get("id"), id));
            return s.createQuery(criteriaQuery).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        });

        if (repo.isEnabled())
            throw new CommandArgumentException("Repository is already enabled.");

        repo.setEnabled(true);

        sender.sendMessage("Enabled repository \"" + repo.getId() + "\".");
    }

    @Command(description = "Adds a repository", permission = "system.repository.add")
    public void add(CommandSender sender,
                        @CommandArgumentLabel.Argument(label = "add") String add,
                        @CommandArgumentString.Argument(label = "id") String id,
                        @CommandArgumentString.Argument(label = "json") String json)
            throws CommandExecutionException, SQLException {
        // make sure ID isn't set
        JsonObject object = new JsonParser().parse(json).getAsJsonObject();
        if (object.has("id")) throw new CommandArgumentException("Repository JSON should not have \"id\" defined.");

        // assert our own id
        object.addProperty("id", id);

        // make sure URL and type are present
        if (!object.has("url")) throw new CommandArgumentException("Repository JSON should have \"url\" defined.");
        if (!object.has("type")) throw new CommandArgumentException("Repository JSON should have \"type\" defined.");

        // try to parse
        Repositories.readRepository(object);

        Repository repo = database.executeTransaction(s -> {
            Repository repository = new Repository(database, id, object.toString());
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

    @Command(description = "Gets repository information", permission = "system.repository.info")
    public void info(CommandSender sender,
                       @CommandArgumentLabel.Argument(label = "info") String info,
                       @CommandArgumentString.Argument(label = "id") String id)
            throws CommandExecutionException {
        Repository repo = database.execute(s -> {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<Repository> criteriaQuery = cb.createQuery(Repository.class);
            Root<Repository> from = criteriaQuery.from(Repository.class);
            criteriaQuery.select(from);
            criteriaQuery.where(cb.equal(from.get("id"), id));
            return s.createQuery(criteriaQuery).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        });

        if (repo == null) throw new CommandArgumentException("Repository not found");

        RemoteRepository remoteRepository = Repositories.readRepository(
                new JsonParser().parse(repo.getJson()).getAsJsonObject()
        );

        sender.details(builder -> builder
                .name("Repository").key(repo.getId())
                .item("URL", remoteRepository.getUrl())
                .item("Enabeled", Boolean.toString(repo.isEnabled()))
                .build()
        ).send();
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
                                .append(" ")
                                .append((repo1.isEnabled() ? "(enabled)" : "(disabled)")))
                        .build()
        ).send();
    }
}
