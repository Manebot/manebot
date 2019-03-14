package com.github.manevolent.jbot.database.model;

import com.github.manevolent.jbot.artifact.ArtifactIdentifier;

import javax.persistence.*;

@Entity
@Table(
        name = "Plugin",
        indexes = {
                @Index(columnList = "packageId,artifactId,version", unique = true)
                @Index(columnList = "enabled")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"packageId","artifactId","version"})}
)
public class HibernatePlugin {

    @Id
    @GeneratedValue
    @Column()
    private int pluginId;

    @Column()
    private String packageId;

    @Column()
    private String artifactId;

    @Column()
    private String version;

    @Column
    private boolean enabled;

    public int getPluginId() {
        return pluginId;
    }

    public void setPluginId(int pluginId) {
        this.pluginId = pluginId;
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
}
