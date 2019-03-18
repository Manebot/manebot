package com.github.manevolent.jbot.database.model;

import javax.persistence.*;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "userId,id,platformId", unique = true),
                @Index(columnList = "platformId,id"),
                @Index(columnList = "platformId")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"platformId","id"})}
)
public class UserAssociation {

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

}
