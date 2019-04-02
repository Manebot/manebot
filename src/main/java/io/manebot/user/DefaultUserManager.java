package io.manebot.user;

import io.manebot.database.Database;
import io.manebot.database.expressions.ExtendedExpressions;
import io.manebot.database.expressions.MatchMode;
import io.manebot.database.model.Entity;
import io.manebot.database.model.EntityType;
import io.manebot.platform.Platform;
import io.manebot.virtual.Virtual;

import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

public final class DefaultUserManager implements UserManager {
    private static final Class<io.manebot.database.model.User> userClass
            = io.manebot.database.model.User.class;

    private static final Class<io.manebot.database.model.Group> groupClass
            = io.manebot.database.model.Group.class;

    private static final Class<io.manebot.database.model.UserAssociation> userAssociationClass
            = io.manebot.database.model.UserAssociation.class;

    private final Database database;

    public DefaultUserManager(Database database) {
        this.database = database;
    }

    @Override
    public User createUser(String username, UserType type) {
        if (getUserByName(username) != null)
            throw new IllegalArgumentException("username", new SQLException("username already exists"));

        Entity entity = new Entity(database, EntityType.USER);

        io.manebot.database.model.User user =
                new io.manebot.database.model.User(database, entity, username, type);

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
            CriteriaQuery<io.manebot.database.model.User> criteriaQuery
                    = cb.createQuery(userClass);

            Root<io.manebot.database.model.User> from = criteriaQuery.from(userClass);
            criteriaQuery.select(from);
            criteriaQuery.where(
                    cb.or(cb.equal(from.get("username"), displayName),
                            ExtendedExpressions.escapedLike(cb, from.get("displayName"), displayName, MatchMode.START))
            );

            return s.createQuery(criteriaQuery).setMaxResults(1).getResultList().stream().findFirst().orElse(null);
        });
    }

    @Override
    public Collection<UserBan> getBans() {
        return Collections.unmodifiableCollection(database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + io.manebot.database.model.UserBan.class.getName() + " x " +
                            "order by x.end desc",
                    io.manebot.database.model.UserBan.class
            ).getResultList();
        }));
    }

    @Override
    public Collection<UserBan> getCurrentBans() {
        return Collections.unmodifiableCollection(database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + io.manebot.database.model.UserBan.class.getName() + " x " +
                            "where x.end > :now " +
                            "order by x.end desc",
                    io.manebot.database.model.UserBan.class
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

        io.manebot.database.model.Group group =
                new io.manebot.database.model.Group(
                        database,
                        entity,
                        name,
                        (io.manebot.database.model.User)
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
