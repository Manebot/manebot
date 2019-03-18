package com.github.manevolent.jbot.database.model;

import com.github.manevolent.jbot.artifact.ArtifactIdentifier;

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
public class Plugin {

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

    @Column(nullable = false)
    private int created;

    @Column(nullable = true)
    private Integer updated;

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

    public Set<PluginConfiguration> getPluginConfigurations() {
        return pluginConfigurations;
    }
}
