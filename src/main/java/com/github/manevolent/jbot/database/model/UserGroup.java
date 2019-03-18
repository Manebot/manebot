package com.github.manevolent.jbot.database.model;

import javax.persistence.*;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "userId,groupId", unique = true),
                @Index(columnList = "created"),
                @Index(columnList = "updated")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"userId", "groupId"})}
)
public class UserGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int xrefId;

    @ManyToOne(optional = false)
    @JoinColumn(name = "userId")
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "groupId")
    private Group group;

    @ManyToOne(optional = false)
    @JoinColumn(name = "addingUserId")
    private User addingUser;

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

    public int getXrefId() {
        return xrefId;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public User getAddingUser() {
        return addingUser;
    }

    public void setAddingUser(User addingUser) {
        this.addingUser = addingUser;
    }
}
