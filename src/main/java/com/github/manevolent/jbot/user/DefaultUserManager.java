package com.github.manevolent.jbot.user;

import com.github.manevolent.jbot.database.Database;
import com.github.manevolent.jbot.database.model.Entity;
import com.github.manevolent.jbot.database.model.EntityType;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.user.*;
import com.github.manevolent.jbot.virtual.Virtual;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;

public final class DefaultUserManager implements UserManager {
    private static final Class<com.github.manevolent.jbot.database.model.User> userClass
            = com.github.manevolent.jbot.database.model.User.class;

    private static final Class<com.github.manevolent.jbot.database.model.Group> groupClass
            = com.github.manevolent.jbot.database.model.Group.class;

    private static final Class<com.github.manevolent.jbot.database.model.UserAssociation> userAssociationClass
            = com.github.manevolent.jbot.database.model.UserAssociation.class;

    private final Database systemDatabase;

    public DefaultUserManager(Database systemDatabase) {
        this.systemDatabase = systemDatabase;
    }

    @Override
    public User createUser(String username, UserType type) {
        if (getUserByName(username) != null)
            throw new IllegalArgumentException("username", new SQLException("username already exists"));

        Entity entity = new Entity(systemDatabase, EntityType.USER);

        com.github.manevolent.jbot.database.model.User user =
                new com.github.manevolent.jbot.database.model.User(systemDatabase, entity, username, type);

        try {
            systemDatabase.executeTransaction(entityManager -> {
                entityManager.persist(entity);
                entityManager.persist(user);
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return user;
    }

    @Override
    public User getUserByName(String username) {
        return systemDatabase.execute(s -> { return s
                .createQuery("from " + User.class.getName() + " u " +
                        "where u.username = :username", User.class)
                .setParameter("username", username)
                .getResultList()
                .stream().findFirst().orElse(null);
        });
    }

    @Override
    public User getUserByDisplayName(String displayName) {
        return systemDatabase.execute(s -> { return s
                .createQuery("from " + userClass.getName() + " u " +
                        "where u.displayName = :displayName or u.username = :displayName", userClass)
                .setParameter("displayName", displayName)
                .getResultList()
                .stream().findFirst().orElse(null);
        });
    }

    @Override
    public Collection<User> getUsers() {
        return systemDatabase.execute(s -> { return new ArrayList<>(s
                .createQuery("from " + userClass.getName(), userClass)
                .getResultList());
        });
    }

    @Override
    public Collection<User> getUsersByType(UserType userType) {
        return systemDatabase.execute(s -> { return new ArrayList<>(
                s.createQuery(
                        "from " + userClass.getName() + " u " +
                                "inner join u.userType t " +
                                "where t = :username",
                        userClass
                )
                        .setParameter("userType", userType)
                        .getResultList());
        });
    }

    @Override
    public UserGroup getUserGroupByName(String groupName) {
        return systemDatabase.execute(s -> { return s
                .createQuery("from " + groupClass.getName() + " u where u.name = :name", groupClass)
                .setParameter("name", groupName)
                .getResultList()
                .stream().findFirst().orElse(null);
        });
    }

    @Override
    public Collection<UserGroup> getUserGroups() {
        return systemDatabase.execute(s -> { return new ArrayList<>(s
                .createQuery("from " + groupClass.getName(), groupClass)
                .getResultList());
        });
    }

    @Override
    public UserGroup createUserGroup(String name) {
        if (getUserGroupByName(name) != null)
            throw new IllegalArgumentException("name", new SQLException("group name already exists"));

        Entity entity = new Entity(systemDatabase, EntityType.USER);

        com.github.manevolent.jbot.database.model.Group group =
                new com.github.manevolent.jbot.database.model.Group(
                        systemDatabase,
                        entity,
                        name,
                        (com.github.manevolent.jbot.database.model.User)
                                Virtual.getInstance().currentProcess().getUser()
                );

        try {
            systemDatabase.executeTransaction(entityManager -> {
                entityManager.persist(entity);
                entityManager.persist(group);
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return group;
    }

    @Override
    public void deleteUserGroup(String s) {

    }

    @Override
    public UserAssociation getUserAssociation(Platform platform, String id) {
        return systemDatabase.execute(s -> { return s
                .createQuery(
                        "from " + userAssociationClass.getName() + " u " +
                                "inner join u.platform p " +
                                "where p.id = :platformId and u.id = :userId",
                        userAssociationClass
                )
                .setParameter("platformId", platform.getId())
                .setParameter("userId", id)
                .getResultList()
                .stream().findFirst().orElse(null);
        });
    }
}
