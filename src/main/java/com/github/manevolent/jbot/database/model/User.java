package com.github.manevolent.jbot.database.model;

import javax.persistence.*;
import java.util.Set;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "username", unique = true),
                @Index(columnList = "displayName"),
                @Index(columnList = "created"),
                @Index(columnList = "updated")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"username"})}
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int userId;

    @Column(nullable = false, name = "userTypeId")
    @Enumerated(EnumType.ORDINAL)
    private UserType userType;

    @Column(length = 64, nullable = false)
    private String username;

    @Column(length = 24, nullable = true)
    private String displayName;

    @Column(nullable = true)
    private Integer lastSeen;

    @ManyToOne(optional = false)
    @JoinColumn(name = "entityId")
    private Entity entity;

    @Column(nullable = false)
    private int created;

    @Column(nullable = true)
    private Integer updated;

    @OneToMany(mappedBy = "user")
    private Set<UserAssociation> userAssociations;

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public int getUserId() {
        return userId;
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

    public int getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(int lastSeen) {
        this.lastSeen = lastSeen;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public Set<UserAssociation> getAssociations() {
        return userAssociations;
    }

}
