package com.github.manevolent.jbot.database.model;

import com.github.manevolent.jbot.artifact.ArtifactIdentifier;
import com.github.manevolent.jbot.database.search.SearchOperator;
import com.github.manevolent.jbot.database.search.handler.ComparingSearchHandler;
import com.github.manevolent.jbot.database.search.handler.SearchHandlerPropertyContains;
import com.github.manevolent.jbot.database.search.handler.SearchHandlerPropertyEquals;
import com.github.manevolent.jbot.database.search.handler.SearchHandlerPropertyIsNull;
import com.github.manevolent.jbot.plugin.PluginProperty;
import com.github.manevolent.jbot.plugin.PluginRegistration;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "packageId,artifactId", unique = true),
                @Index(columnList = "enabled")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"packageId","artifactId"})}
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Plugin extends TimedRow {
    @Transient
    private com.github.manevolent.jbot.plugin.PluginRegistration registration;

    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public Plugin(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

    public Plugin(com.github.manevolent.jbot.database.Database database, ArtifactIdentifier artifactIdentifier) {
        this.database = database;

        this.setArtifactIdentifier(artifactIdentifier);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int pluginId;

    @Column(length = 64, nullable = false)
    private String packageId;

    @Column(length = 64, nullable = false)
    private String artifactId;

    @Column(length = 128, nullable = false)
    private String version;

    @Column(nullable = false)
    private boolean enabled;

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
        try {
            this.enabled = database.executeTransaction(s -> {
                Plugin plugin = s.find(Plugin.class, getPluginId());
                return plugin.enabled = enabled;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(pluginId);
    }

    public PluginRegistration getRegistration() {
        return registration;
    }

    public void setRegistration(PluginRegistration registration) {
        this.registration = registration;
    }

    public void setVersion(String version) {
        try {
            this.version = database.executeTransaction(s -> {
                Plugin plugin = s.find(Plugin.class, getPluginId());
                return plugin.version = version;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Collection<com.github.manevolent.jbot.database.model.PluginProperty> getProperties() {
        return Collections.unmodifiableCollection(database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + com.github.manevolent.jbot.database.model.PluginProperty.class.getName()
                            + " x " +
                            "inner join x.plugin p "+
                            "where p.pluginId = :pluginId",
                    com.github.manevolent.jbot.database.model.PluginProperty.class
            ).setParameter("pluginId", getPluginId()).getResultList();
        }));
    }

    public com.github.manevolent.jbot.database.model.PluginProperty getProperty(String name) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + com.github.manevolent.jbot.database.model.PluginProperty.class.getName()
                            + " x " +
                            "inner join x.plugin p "+
                            "where p.pluginId = :pluginId and x.name = :name",
                    com.github.manevolent.jbot.database.model.PluginProperty.class
            ).setParameter("pluginId", getPluginId()).setParameter("name", name)
                    .getResultList()
                    .stream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public void setProperty(String name, String value) {
        com.github.manevolent.jbot.database.model.PluginProperty property = getProperty(name);
        if (property != null) {
            if (value != null)
                property.setValue(value);
            else {
                property.remove();
            }

            return;
        }

        if (value != null) {
            try {
                database.executeTransaction(s -> {
                    com.github.manevolent.jbot.database.model.PluginProperty newProperty =
                            new com.github.manevolent.jbot.database.model.PluginProperty(
                                    database,
                                    s.find(Plugin.class, getPluginId()),
                                    name,
                                    value
                            );

                    s.persist(newProperty);
                });
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
