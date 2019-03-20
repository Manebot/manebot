package com.github.manevolent.jbot.database.model;

import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

@javax.persistence.Entity
@Table(
        uniqueConstraints = {
                @UniqueConstraint(columnNames = { "pluginId", "name" })
        },
        indexes = {
                @Index(columnList = "name", unique = false),
                @Index(columnList = "pluginId,name", unique = true)
        }
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Database extends TimedRow {
    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public Database(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "databaseId")
    private int databaseId;

    @Column(length = 64, nullable = false)
    private String name;

    @ManyToOne(optional = true)
    @JoinColumn(name = "pluginId")
    private Plugin plugin;

    public int getDatabaseId() {
        return databaseId;
    }

    public void setDatabaseId(int databaseId) {
        this.databaseId = databaseId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public void setPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(databaseId);
    }
}
