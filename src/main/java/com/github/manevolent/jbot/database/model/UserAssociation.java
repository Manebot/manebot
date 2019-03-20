package com.github.manevolent.jbot.database.model;

import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.sql.SQLException;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "userId"),
                @Index(columnList = "id"),
                @Index(columnList = "platformId,id", unique = true),
                @Index(columnList = "platformId")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"platformId","id"})}
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class UserAssociation implements com.github.manevolent.jbot.user.UserAssociation {
    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public UserAssociation(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

    public UserAssociation(com.github.manevolent.jbot.database.Database database,
                           Platform platform,
                           String id,
                           User user) {
        this(database);

        this.platform = platform;
        this.id = id;
        this.user = user;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int userAssociationId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "userId")
    private User user;

    @Column(length = 128, nullable = false)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "platformId")
    private Platform platform;

    @Column(nullable = false)
    private int created;

    @Column(nullable = true)
    private Integer updated;

    public int getUserAssociationId() {
        return userAssociationId;
    }

    public User getUser() {
        return user;
    }

    @Override
    public String getPlatformId() {
        return id;
    }

    @Override
    public void remove() {
        try {
            database.executeTransaction(s -> {
                s.remove(UserAssociation.this);
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        this.platform = platform;
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

    @Override
    public int hashCode() {
        return Integer.hashCode(userAssociationId);
    }
}
