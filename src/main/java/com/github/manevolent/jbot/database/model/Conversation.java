package com.github.manevolent.jbot.database.model;

import javax.persistence.*;
import java.util.Set;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "platformId,id", unique = true),
                @Index(columnList = "created"),
                @Index(columnList = "updated")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"platformId", "id"})}
)
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int conversationId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "entityId")
    private Entity entity;

    @ManyToOne(optional = false)
    @JoinColumn(name = "platformId")
    private Platform platform;

    @Column(length = 128, nullable = false)
    private String id;

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

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public int getConversationId() {
        return conversationId;
    }
}
