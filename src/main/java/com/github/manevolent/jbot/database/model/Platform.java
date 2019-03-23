package com.github.manevolent.jbot.database.model;

import com.github.manevolent.jbot.platform.PlatformRegistration;
import com.github.manevolent.jbot.user.User;
import com.github.manevolent.jbot.user.UserAssociation;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.util.Collection;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "id", unique = true),
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"id"})}
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Platform extends TimedRow implements com.github.manevolent.jbot.platform.Platform {
    /**
     * Platform connection
     */
    @Transient
    private PlatformRegistration registration;

    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public Platform(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

    public Platform(com.github.manevolent.jbot.database.Database database, String id) {
        this(database);

        this.id = id;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int platformId;

    @Column(length = 64, nullable = false)
    private String id;

    public int getPlatformId() {
        return platformId;
    }

    @Override
    public PlatformRegistration getRegistration() {
        return registration;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public UserAssociation getUserAssocation(String id) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + com.github.manevolent.jbot.database.model.UserAssociation.class.getName() + " x " +
                            "inner join x.platform p " +
                            "where p.id = :platformId and x.id = :userId",
                    com.github.manevolent.jbot.database.model.UserAssociation.class
            )
                    .setMaxResults(1)
                    .setParameter("platformId", getId())
                    .setParameter("userId", id)
                    .getResultList()
                    .stream()
                    .findFirst()
                    .orElse(null);
        });
    }

    @Override
    public Collection<UserAssociation> getUserAssociations(User user) {
        throw new UnsupportedOperationException(); // TODO
    }

    @Override
    public Collection<UserAssociation> getUserAssociations() {
        throw new UnsupportedOperationException(); // TODO
    }

    public com.github.manevolent.jbot.plugin.Plugin getPlugin() {
        return this.registration == null ? null : this.registration.getPlugin();
    }

    public void setRegistration(PlatformRegistration registration) {
        this.registration = registration;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(platformId);
    }
}
