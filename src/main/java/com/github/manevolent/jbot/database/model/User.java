package com.github.manevolent.jbot.database.model;

import com.github.manevolent.jbot.cache.CachedValue;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.security.Grant;
import com.github.manevolent.jbot.security.GrantedPermission;
import com.github.manevolent.jbot.security.Permission;
import com.github.manevolent.jbot.user.UserBan;
import com.github.manevolent.jbot.user.UserGroupMembership;
import com.github.manevolent.jbot.user.UserType;
import com.github.manevolent.jbot.virtual.Virtual;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "username", unique = true),
                @Index(columnList = "displayName"),
                @Index(columnList = "created"),
                @Index(columnList = "updated")
        },
        uniqueConstraints = {@UniqueConstraint(columnNames ={"username"})}
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class User extends TimedRow implements com.github.manevolent.jbot.user.User {
    @Transient
    private final CachedValue<com.github.manevolent.jbot.database.model.UserBan> banCachedValue =
            new CachedValue<>(1000L, new Supplier<com.github.manevolent.jbot.database.model.UserBan>() {
                @Override
                public com.github.manevolent.jbot.database.model.UserBan get() {
                    return database.execute(s -> {
                        return s.createQuery(
                                "SELECT x FROM " +
                                        com.github.manevolent.jbot.database.model.UserBan.class.getName() + " x " +
                                        "inner join x.user u " +
                                        "where u.userId = :userId and x.end > :time and x.pardoned = false " +
                                        "order by x.end desc",
                                com.github.manevolent.jbot.database.model.UserBan.class
                        )
                                .setParameter("userId", getUserId())
                                .setParameter("time", (int) (System.currentTimeMillis() / 1000L))
                                .setMaxResults(1)
                                .getResultList()
                                .stream().findFirst().orElse(null);
                    });
                }
            });

    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public User(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

    public User(com.github.manevolent.jbot.database.Database database,
                Entity entity,
                String username,
                UserType type) {
        this(database);

        this.entity = entity;
        this.username = username;
        this.userType = type;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column()
    private int userId;

    @Column(nullable = false, name = "userTypeId")
    @Enumerated(EnumType.ORDINAL)
    private UserType userType;

    @Column(length = 64, nullable = false)
    private String username;

    @Column(length = 24, nullable = true)
    private String displayName;

    @Column(nullable = true)
    private Integer lastSeen;

    @ManyToOne(optional = false)
    @JoinColumn(name = "entityId")
    private Entity entity;

    @ManyToOne(optional = true)
    @JoinColumn(name = "privateConversationId")
    private Conversation conversation;

    public String getDisplayName() {
        return displayName == null ? username : displayName;
    }

    @Override
    public Date getRegisteredDate() {
        return getCreatedDate();
    }

    @Override
    public Date getLastSeenDate() {
        return lastSeen == null ? null : new Date((long) lastSeen * 1000L);
    }

    @Override
    public void setLastSeenDate(Date date) {
        setLastSeen((int) (date.getTime() / 1000));
    }

    @Override
    public Collection<UserGroupMembership> getMembership() {
        return Collections.unmodifiableCollection(database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + com.github.manevolent.jbot.database.model.UserGroup.class.getName() + " x " +
                            "inner join x.user u " +
                            "where u.userId = :userId",
                    com.github.manevolent.jbot.database.model.UserGroup.class
            ).setParameter("userId", getUserId()).getResultList();
        }));
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public int getUserId() {
        return userId;
    }

    public void setLastSeen(int lastSeen) {
        try {
            database.executeTransaction(s -> {
                User user = s.find(User.class, getUserId());
                user.lastSeen = lastSeen;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public com.github.manevolent.jbot.entity.Entity getEntity() {
        return entity;
    }

    // Override for efficiency
    @Override
    public com.github.manevolent.jbot.database.model.UserAssociation getUserAssociation(Platform platform, String id) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + com.github.manevolent.jbot.database.model.UserAssociation.class.getName() + " x " +
                            "inner join x.platform p " +
                            "where p.id = :platformId and x.id = :userId",
                    com.github.manevolent.jbot.database.model.UserAssociation.class
            )
                    .setMaxResults(1)
                    .setParameter("platformId", platform.getId())
                    .setParameter("userId", id)
                    .getResultList()
                    .stream()
                    .findFirst()
                    .orElse(null);
        });
    }

    public Collection<com.github.manevolent.jbot.user.UserAssociation> getAssociations() {
        return Collections.unmodifiableCollection(database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + com.github.manevolent.jbot.database.model.UserAssociation.class.getName() + " x " +
                            "inner join x.user u " +
                            "where u.userId = :userId",
                    com.github.manevolent.jbot.database.model.UserAssociation.class
            ).setParameter("userId", getUserId()).getResultList();
        }));
    }

    @Override
    public Collection<UserBan> getBans() {
        return Collections.unmodifiableCollection(database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + com.github.manevolent.jbot.database.model.UserBan.class.getName() + " x " +
                            "inner join x.user u " +
                            "where u.userId = :userId",
                    com.github.manevolent.jbot.database.model.UserBan.class
            ).setParameter("userId", getUserId()).getResultList();
        }));
    }

    @Override
    public Collection<UserBan> getIssuedBans() {
        return Collections.unmodifiableCollection(database.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + com.github.manevolent.jbot.database.model.UserBan.class.getName() + " x " +
                            "inner join x.banningUser u " +
                            "where u.userId = :userId",
                    com.github.manevolent.jbot.database.model.UserBan.class
            ).setParameter("userId", getUserId()).getResultList();
        }));
    }

    @Override
    public UserBan getBan() {
        // Cache this value so spamming doesn't spam queries
        return banCachedValue.get();
    }

    @Override
    public UserBan ban(String reason, Date date) throws SecurityException {
        Permission.checkPermission("system.user.ban");

        if (Virtual.getInstance().currentProcess().getUser() == this)
            throw new SecurityException("Cannot ban own user");

        if (getType() == UserType.SYSTEM &&
                Virtual.getInstance().currentProcess().getUser().getType() != UserType.SYSTEM)
            throw new SecurityException("Cannot ban system user");

        if (getBan() != null)
            throw new IllegalArgumentException("User is already banned");

        User banningUser = (User) Virtual.getInstance().currentProcess().getUser();

        try {
            return database.executeTransaction(s -> {
                User userAttached = s.find(User.class, getUserId());
                User banningUserAttached = s.find(User.class, banningUser.getUserId());

                com.github.manevolent.jbot.database.model.UserBan userBan =
                        new com.github.manevolent.jbot.database.model.UserBan(
                                database,
                                userAttached,
                                banningUserAttached,
                                (int) (date.getTime() / 1000),
                                reason
                        );

                s.persist(userBan);

                // Unset ban cached value
                banCachedValue.unset();

                return userBan;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public com.github.manevolent.jbot.database.model.UserAssociation createAssociation(
            Platform platform,
            String platformUserId
    ) {
        com.github.manevolent.jbot.database.model.UserAssociation association =
                getUserAssociation(platform, platformUserId);

        if (association != null) return association;

        try {
            association = database.executeTransaction(s -> {
                UserAssociation newAssocation =
                        new UserAssociation(
                                database,
                                (com.github.manevolent.jbot.database.model.Platform) platform,
                                platformUserId,
                                User.this
                        );

                s.persist(newAssocation);

                return newAssocation;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return association;
    }

    @Override
    public boolean removeAssociation(Platform platform, String platformUserId) {
        com.github.manevolent.jbot.database.model.UserAssociation association =
                getUserAssociation(platform, platformUserId);

        if (association != null) {
            try {
                database.executeTransaction(s -> {
                    s.remove(association);
                });
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

            return true;
        } else return false;
    }

    @Override
    public Conversation getPrivateConversation() {
        return conversation;
    }

    @Override
    public void setPrivateConversation(com.github.manevolent.jbot.conversation.Conversation conversation) {
        Conversation mapped = (Conversation) conversation;

        try {
            database.executeTransaction(s -> {
                User user = s.find(User.class, getUserId());
                user.conversation = mapped;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public UserType getType() {
        return userType;
    }

    @Override
    public boolean setType(UserType userType) {
        if (this.userType == userType) return false;

        try {
            return database.executeTransaction(s -> {
                User user = s.find(User.class, getUserId());
                user.userType = userType;
                return true;
            });
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean hasPermission(Permission permission, Grant fallback) {
        // If the user is a system user, all permissions are ignored.
        if (getType() == UserType.SYSTEM) return true;

        // Get existing permission (always shortcuts fallback)
        GrantedPermission existing = getEntity().getPermission(permission);
        if (existing != null) return existing.getGrant() == Grant.ALLOW;

        // Look through groups with explicit DENY flattening
        Collection<Grant> groupGrants = getGroups().stream()
                .map(group -> {
                    GrantedPermission grantedPermission = group.getEntity().getPermission(permission);
                    if (grantedPermission == null) return null;
                    return grantedPermission.getGrant();
                })
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        // DENY first, then ALLOW
        if (groupGrants.contains(Grant.DENY)) return false;
        else if (groupGrants.contains(Grant.ALLOW)) return true;

        // Fallback, no explicit permissions were supplied.
        return fallback == Grant.ALLOW;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(userId);
    }
}
