package com.github.manevolent.jbot.user;

import com.github.manevolent.jbot.database.Database;
import com.github.manevolent.jbot.database.expressions.EscapedLike;
import com.github.manevolent.jbot.database.model.Entity;
import com.github.manevolent.jbot.database.model.EntityType;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.virtual.Virtual;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public final class DefaultUserManager implements UserManager {
    private static final Class<com.github.manevolent.jbot.database.model.User> userClass
            = com.github.manevolent.jbot.database.model.User.class;

    private static final Class<com.github.manevolent.jbot.database.model.Group> groupClass
            = com.github.manevolent.jbot.database.model.Group.class;

    private static final Class<com.github.manevolent.jbot.database.model.UserAssociation> userAssociationClass
            = com.github.manevolent.jbot.database.model.UserAssociation.class;

    private final Database database;

    public DefaultUserManager(Database database) {
        this.database = database;
    }

    @Override
    public User createUser(String username, UserType type) {
        if (getUserByName(username) != null)
            throw new IllegalArgumentException("username", new SQLException("username already exists"));

        Entity entity = new Entity(database, EntityType.USER);

        com.github.manevolent.jbot.database.model.User user =
                new com.github.manevolent.jbot.database.model.User(database, entity, username, type);

        try {
            database.executeTransaction(entityManager -> {
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
        return database.execute(s -> { return s
                .createQuery("from " + User.class.getName() + " u " +
                        "where u.username = :username", User.class)
                .setMaxResults(1)
                .setParameter("username", username)
                .getResultList()
                .stream().findFirst().orElse(null);
        });
    }

    @Override
    public User getUserByDisplayName(String displayName) {
        return database.execute(s -> {
            CriteriaBuilder cb = s.getCriteriaBuilder();
            CriteriaQuery<com.github.manevolent.jbot.database.model.User> criteriaQuery
                    = cb.createQuery(userClass);

            Root<com.github.manevolent.jbot.database.model.User> from = criteriaQuery.from(userClass);
            criteriaQuery.select(from);

            criteriaQuery.where(
                    cb.or(cb.equal(from.get("username"), displayName),
                            cb.like(from.get("displayName"), EscapedLike.escape(displayName) + "%", '!'))
            );

            return s.createQuery(criteriaQuery).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        });
    }

    @Override
    public Collection<UserBan> getBans() {
        return Collections.unmodifiableCollection(database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + com.github.manevolent.jbot.database.model.UserBan.class.getName() + " x " +
                            "order by x.end desc",
                    com.github.manevolent.jbot.database.model.UserBan.class
            ).getResultList();
        }));
    }

    @Override
    public Collection<UserBan> getCurrentBans() {
        return Collections.unmodifiableCollection(database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + com.github.manevolent.jbot.database.model.UserBan.class.getName() + " x " +
                            "where x.end > :now " +
                            "order by x.end desc",
                    com.github.manevolent.jbot.database.model.UserBan.class
            ).setParameter("now", (int) (System.currentTimeMillis() / 1000)).getResultList();
        }));
    }

    @Override
    public Collection<User> getUsers() {
        return database.execute(s -> { return new ArrayList<>(s
                .createQuery("from " + userClass.getName(), userClass)
                .getResultList());
        });
    }

    @Override
    public Collection<User> getUsersByType(UserType userType) {
        return database.execute(s -> { return new ArrayList<>(
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
        return database.execute(s -> { return s
                .createQuery("from " + groupClass.getName() + " u where u.name = :name", groupClass)
                .setParameter("name", groupName)
                .getResultList()
                .stream().findFirst().orElse(null);
        });
    }

    @Override
    public Collection<UserGroup> getUserGroups() {
        return database.execute(s -> { return new ArrayList<>(s
                .createQuery("from " + groupClass.getName(), groupClass)
                .getResultList());
        });
    }

    @Override
    public UserGroup createUserGroup(String name) {
        if (getUserGroupByName(name) != null)
            throw new IllegalArgumentException("name", new SQLException("group name already exists"));

        Entity entity = new Entity(database, EntityType.USER);

        com.github.manevolent.jbot.database.model.Group group =
                new com.github.manevolent.jbot.database.model.Group(
                        database,
                        entity,
                        name,
                        (com.github.manevolent.jbot.database.model.User)
                                Virtual.getInstance().currentProcess().getUser()
                );

        try {
            database.executeTransaction(entityManager -> {
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
        throw new UnsupportedOperationException();
    }

    @Override
    public UserAssociation getUserAssociation(Platform platform, String id) {
        return database.execute(s -> { return s
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
