package com.github.manevolent.jbot.database.model;

import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "pluginId,name", unique = true)
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"pluginId","name"})}
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class PluginConfiguration extends TimedRow {
    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public PluginConfiguration(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

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

    @Override
    public int hashCode() {
        return Integer.hashCode(pluginConfigurationId);
    }
}
