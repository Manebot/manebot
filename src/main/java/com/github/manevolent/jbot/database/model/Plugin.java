package com.github.manevolent.jbot.database.model;

import com.github.manevolent.jbot.artifact.ArtifactIdentifier;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.util.Set;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "packageId,artifactId,version", unique = true),
                @Index(columnList = "enabled")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"packageId","artifactId","version"})}
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Plugin extends TimedRow {
    @Transient
    private com.github.manevolent.jbot.plugin.Plugin plugin;

    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public Plugin(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int pluginId;

    @Column(length = 64, nullable = false)
    private String packageId;

    @Column(length = 64, nullable = false)
    private String artifactId;

    @Column(length = 32, nullable = false)
    private String version;

    @Column(nullable = false)
    private boolean enabled;

    @OneToMany(mappedBy = "plugin")
    private Set<Database> databases;

    @OneToMany(mappedBy = "plugin")
    private Set<PluginConfiguration> pluginConfigurations;

    public int getPluginId() {
        return pluginId;
    }

    public ArtifactIdentifier getArtifactIdentifier() {
        return new ArtifactIdentifier(packageId, artifactId, version);
    }

    public void setArtifactIdentifier(ArtifactIdentifier id) {
        this.packageId = id.getPackageId();
        this.artifactId = id.getArtifactId();
        this.version = id.getVersion();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<Database> getDatabases() {
        return databases;
    }

    public Set<PluginConfiguration> getPluginConfigurations() {
        return pluginConfigurations;
    }

    public com.github.manevolent.jbot.plugin.Plugin getPlugin() {
        return plugin;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(pluginId);
    }
}
