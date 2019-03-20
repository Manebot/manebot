package com.github.manevolent.jbot.database.model;

import com.github.manevolent.jbot.property.Property;
import com.github.manevolent.jbot.security.Grant;
import com.github.manevolent.jbot.security.GrantedPermission;
import com.github.manevolent.jbot.virtual.Virtual;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

@javax.persistence.Entity
@Table(
        indexes = {
                @Index(columnList = "entityTypeId"),
                @Index(columnList = "created"),
                @Index(columnList = "updated")
        }
)
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
public class Entity extends TimedRow implements com.github.manevolent.jbot.entity.Entity {
    @Transient
    private final com.github.manevolent.jbot.database.Database database;
    public Entity(com.github.manevolent.jbot.database.Database database) {
        this.database = database;
    }

    public Entity(com.github.manevolent.jbot.database.Database database, EntityType type) {
        this(database);

        this.entityType = type;
    }

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

    public int getEntityId() {
        return entityId;
    }

    @Override
    public String getName() {
        return String.format("entity/%d", getEntityId());
    }

    @Override
    public Collection<Property> getProperties() {
        return null;
    }

    @Override
    public Property getPropery(String node) {
        return null;
    }

    @Override
    public Permission getPermission(String node) {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT p FROM " + Permission.class.getName() + " p " +
                            "inner join p.entity e " +
                            "where e.entityId = :entityId and p.node = :node",
                    Permission.class
            )
                    .setParameter("entityId", entityId)
                    .setParameter("node", node)
                    .getResultList()
                    .stream()
                    .findFirst()
                    .orElse(null);
        });
    }

    @Override
    public GrantedPermission setPermission(String node, Grant grant) {
        Permission permission = getPermission(node);

        if (permission == null) {
            try {
                permission = database.executeTransaction(tran -> {
                    Permission newPermission = new Permission(
                            database,
                            this,
                            (User) Virtual.getInstance().currentProcess().getUser(),
                            node,
                            grant == Grant.ALLOW
                    );

                    tran.persist(newPermission);

                    return newPermission;
                });
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        } else if (permission.getGrant() != grant) {
            permission.setGrant(grant);
        }

        return permission;
    }

    @Override
    public void removePermission(String s) {

    }

    @Override
    public Collection<GrantedPermission> getPermissions() {
        return new ArrayList<>(permissions);
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(entityId);
    }
}
