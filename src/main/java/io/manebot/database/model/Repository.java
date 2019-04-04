package io.manebot.database.model;
import javax.persistence.*;
import java.sql.SQLException;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "id", unique = true)
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"id"})}
)
public class Repository extends TimedRow {
    @Transient
    private final io.manebot.database.Database database;

    public Repository(io.manebot.database.Database database) {
        this.database = database;
        this.enabled = true;
    }

    public Repository(io.manebot.database.Database database,
                      String id,
                      String json) {
        this(database);

        this.id = id;
        this.json = json;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int repositoryId;

    @Column()
    private String id;

    @Column()
    private String json;

    @Column()
    private boolean enabled;

    public int getRepositoryId() {
        return repositoryId;
    }

    public String getId() {
        return id;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getJson() {
        return json;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(repositoryId);
    }

    public void remove() throws SecurityException {
        try {
            database.executeTransaction(s -> {
                Repository repository = s.find(Repository.class, getRepositoryId());
                s.remove(repository);
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setEnabled(boolean enabled) throws SecurityException {
        try {
            database.executeTransaction(s -> {
                Repository repository = s.find(Repository.class, getRepositoryId());
                return repository.enabled = enabled;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
