package com.github.manevolent.jbot.database.model;

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
public class Database {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "databaseId")
    private int databaseId;

    @Column(length = 64, nullable = false)
    private String name;

    @ManyToOne(optional = true)
    @JoinColumn(name = "pluginId")
    private Plugin plugin;

    @Column(nullable = false)
    private int created;

    @Column(nullable = true)
    private Integer updated;

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

    public Integer getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
    }
}
