package com.github.manevolent.jbot.database.model;

import javax.persistence.*;
import java.util.Set;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "groupId", unique = true),
                @Index(columnList = "name", unique = true),
                @Index(columnList = "owningUserId"),
                @Index(columnList = "entityId"),
                @Index(columnList = "created"),
                @Index(columnList = "updated")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"name"})}
)
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int groupId;

    @Column(length = 64, nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean admin;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owningUserId")
    private User owningUser;

    @ManyToOne(optional = false)
    @JoinColumn(name = "entityId")
    private Entity entity;

    @Column(nullable = false)
    private int created;

    @Column(nullable = true)
    private Integer updated;

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

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getGroupId() {
        return groupId;
    }

    public User getOwningUser() {
        return owningUser;
    }

    public void setOwningUser(User owningUser) {
        this.owningUser = owningUser;
    }
}
