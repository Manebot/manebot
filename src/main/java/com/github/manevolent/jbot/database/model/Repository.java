package com.github.manevolent.jbot.database.model;

import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.sql.SQLException;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "url", unique = true)
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"url"})}
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Repository extends TimedRow {
    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public Repository(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

    public Repository(com.github.manevolent.jbot.database.Database database,
                      String url) {
        this(database);

        this.url = url;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int repositoryId;

    @Column()
    private String url;

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

    public String getUrl() {
        return url;
    }
}
