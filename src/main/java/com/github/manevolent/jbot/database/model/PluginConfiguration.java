package com.github.manevolent.jbot.database.model;

import javax.persistence.*;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "pluginId,name", unique = true)
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"pluginId","name"})}
)
public class PluginConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int pluginConfigurationId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pluginId")
    private Plugin plugin;

    @Column(length = 64, nullable = false)
    private String name;

    @Column(nullable = true)
    private String value;

    @Column(nullable = false)
    private int created;

    @Column(nullable = true)
    private Integer updated;

    public int getPluginConfigurationId() {
        return pluginConfigurationId;
    }

    public Plugin getPlugin() {
        return plugin;
    }

    public void setPlugin(Plugin plugin) {
        this.plugin = plugin;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public int getCreated() {
        return created;
    }

    public void setCreated(int created) {
        this.created = created;
    }

    public int getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }
}
