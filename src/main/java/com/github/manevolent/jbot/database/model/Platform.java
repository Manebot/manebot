package com.github.manevolent.jbot.database.model;

import javax.persistence.*;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "pluginId,name", unique = true),
                @Index(columnList = "name")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"pluginId","name"})}
)
public class Platform {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int platformId;

    @ManyToOne(optional = true)
    @JoinColumn(name = "pluginId")
    private Plugin plugin;

    @Column(length = 64, nullable = false)
    private String name;

    @Column(nullable = false)
    private int created;

    @Column(nullable = true)
    private Integer updated;

    public int getPlatformId() {
        return platformId;
    }

    public String getName() {
        return name;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
    }

    public Integer getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }
}
