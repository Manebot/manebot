package com.github.manevolent.jbot.database.model;

import javax.persistence.*;
import java.util.Set;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "entityTypeId"),
                @Index(columnList = "created"),
                @Index(columnList = "updated")
        }
)
public class Entity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int entityId;

    @Column(nullable = false, name = "entityTypeId")
    @Enumerated(EnumType.ORDINAL)
    private EntityType entityType;

    @OneToMany(mappedBy = "entity")
    private Set<User> users;

    @OneToMany(mappedBy = "entity")
    private Set<Conversation> conversations;

    @OneToMany(mappedBy = "entity")
    private Set<Permission> permissions;

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

    public Set<User> getUsers() {
        return users;
    }

    public int getEntityId() {
        return entityId;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public void setEntityType(EntityType entityType) {
        this.entityType = entityType;
    }

    public Set<Conversation> getConversations() {
        return conversations;
    }
}
