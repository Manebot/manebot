package com.github.manevolent.jbot.database.model;

import com.github.manevolent.jbot.user.UserGroup;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

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
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Group extends TimedRow implements UserGroup {
    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public Group(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int groupId;

    @Column(length = 64, nullable = false)
    private String name;

    @ManyToOne(optional = false)
    @JoinColumn(name = "owningUserId")
    private User owningUser;

    @ManyToOne(optional = false)
    @JoinColumn(name = "entityId")
    private Entity entity;

    @OneToMany(mappedBy = "group")
    private Set<com.github.manevolent.jbot.database.model.UserGroup> userGroups;

    public Entity getEntity() {
        return entity;
    }

    public void setEntity(Entity entity) {
        this.entity = entity;
    }

    public String getName() {
        return name;
    }

    @Override
    public com.github.manevolent.jbot.user.User getOwner() {
        return owningUser;
    }

    @Override
    public Collection<com.github.manevolent.jbot.user.User> getUsers() {
        return Collections.unmodifiableCollection(
                userGroups.stream()
                .map(com.github.manevolent.jbot.database.model.UserGroup::getUser)
                .collect(Collectors.toList())
        );
    }

    @Override
    public void addUser(com.github.manevolent.jbot.user.User user) throws SecurityException {

    }

    @Override
    public void removeUser(com.github.manevolent.jbot.user.User user) throws SecurityException {

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

    @Override
    public int hashCode() {
        return Integer.hashCode(groupId);
    }
}
