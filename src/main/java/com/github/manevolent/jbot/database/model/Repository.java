package com.github.manevolent.jbot.database.model;

import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.sql.SQLException;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "id", unique = true)
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"id"})}
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Repository extends TimedRow {
    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public Repository(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

    public Repository(com.github.manevolent.jbot.database.Database database,
                      String id,
                      String type,
                      String url) {
        this(database);

        this.id = id;
        this.type = type;
        this.url = url;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int repositoryId;

    @Column()
    private String id;

    @Column()
    private String type;

    @Column()
    private String url;

    public String getUrl() {
        return url;
    }

    public int getRepositoryId() {
        return repositoryId;
    }

    public void remove() throws SecurityException {
        try {
            database.executeTransaction(s -> {
                Repository permission = s.find(Repository.class, getRepositoryId());
                s.remove(permission);
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(repositoryId);
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }
}
