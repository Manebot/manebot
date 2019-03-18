package com.github.manevolent.jbot.database.model;

import javax.persistence.*;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "entityId,node", unique = true),
                @Index(columnList = "node")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"entityId","node"})}
)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int permissionId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "entityId")
    private Entity entity;

    @ManyToOne(optional = false)
    @JoinColumn(name = "grantingUserId")
    private User grantingUser;

    @Column(length = 64, nullable = false)
    private String node;

    @Column(nullable = false)
    private boolean allow;

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

    public Integer getUpdated() {
        return updated;
    }

    public void setUpdated(int updated) {
        this.updated = updated;
    }

    public boolean isAllow() {
        return allow;
    }

    public void setAllow(boolean allow) {
        this.allow = allow;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public User getGrantingUser() {
        return grantingUser;
    }

    public void setGrantingUser(User grantingUser) {
        this.grantingUser = grantingUser;
    }

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public int getPermissionId() {
        return permissionId;
    }
}
