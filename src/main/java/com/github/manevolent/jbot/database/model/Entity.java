package com.github.manevolent.jbot.database.model;

import com.github.manevolent.jbot.property.Property;
import com.github.manevolent.jbot.security.Grant;
import com.github.manevolent.jbot.security.GrantedPermission;
import com.github.manevolent.jbot.virtual.Virtual;
import com.google.common.collect.MapMaker;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final Map<String, Property> propertyMap = new MapMaker().weakValues().makeMap();

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

    public int getEntityId() {
        return entityId;
    }

    @Override
    public String getName() {
        return String.format("entity/%d", getEntityId());
    }

    @Override
    public Collection<Property> getProperties() {
        return Collections.unmodifiableCollection(database.execute(s -> {
            return s.createQuery(
                    "SELECT p FROM " + com.github.manevolent.jbot.database.model.Property.class.getName() + " p " +
                            "inner join p.entity e " +
                            "where e.entityId = :entityId",
                    com.github.manevolent.jbot.database.model.Property.class
            ).setParameter("entityId", entityId).getResultList()
                    .stream()
                    .map(databaseType -> getPropery(databaseType.getName()))
                    .collect(Collectors.toCollection(ArrayList::new));
        }));
    }

    @Override
    public Property getPropery(String node) {
        return propertyMap.computeIfAbsent(node, VirtualProperty::new);
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
                    .setMaxResults(1)
                    .setParameter("entityId", entityId)
                    .setParameter("node", node)
                    .getResultList()
                    .stream()
                    .findFirst()
                    .orElse(null);
        });
    }

    @Override
    public GrantedPermission setPermission(String node, Grant grant) throws SecurityException {
        com.github.manevolent.jbot.security.Permission.checkPermission(node);

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
        Permission permission = getPermission(s);
        if (permission == null) throw new IllegalArgumentException("Permission not found");
        permission.remove();
    }

    @Override
    public Collection<GrantedPermission> getPermissions() {
        return database.execute(s -> {
            return s.createQuery(
                    "SELECT p FROM " + Permission.class.getName() + " p " +
                            "inner join p.entity e " +
                            "where e.entityId = :entityId",
                    Permission.class
            )
                    .setParameter("entityId", entityId)
                    .getResultList()
                    .stream()
                    .map(x -> (GrantedPermission) x)
                    .collect(Collectors.toList());
        });
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(entityId);
    }

    private class VirtualProperty extends BinaryProperty implements Property {
        private static final String ENCODING = "UTF-16";
        private final String name;
        private final Object accessLock = new Object();

        private com.github.manevolent.jbot.database.model.Property property;

        private VirtualProperty(String name) {
            this.name = name;
            this.property = getProperty();
        }

        private com.github.manevolent.jbot.database.model.Property getProperty() {
            if (property == null)
                property = database.execute(s -> {
                    return s.createQuery(
                            "SELECT p FROM " + com.github.manevolent.jbot.database.model.Property.class.getName() + " p " +
                                    "inner join p.entity e " +
                                    "where e.entityId = :entityId and p.name = :name",
                            com.github.manevolent.jbot.database.model.Property.class
                    )
                            .setMaxResults(1)
                            .setParameter("entityId", entityId)
                            .setParameter("name", name)
                            .getResultList()
                            .stream()
                            .findFirst()
                            .orElse(null);
                });

            return property;
        }

        private byte[] getValue() {
            synchronized (accessLock) {
                if (property == null) {
                    return null;
                } else
                    return property.getValue();
            }
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void unset() {
            synchronized (accessLock) {
                if (property == null) return;

                try {
                    this.property = database.executeTransaction(s -> {
                        com.github.manevolent.jbot.database.model.Property attachedProperty =
                                s.find(com.github.manevolent.jbot.database.model.Property.class,
                                        property.getPropertyId());

                        if (attachedProperty != null)
                            s.remove(attachedProperty);

                        return null;
                    });
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }
        }

        @Override
        public boolean isNull() {
            return getValue() == null;
        }

        @Override
        public int size() {
            byte[] value = getValue();
            if (value == null) return -1;
            return value.length;
        }

        @Override
        public int write(byte[] bytes, int offs, int len) {
            byte[] b;

            if (offs == 0 && len == bytes.length)
                b = bytes;
            else {
                b = new byte[len];
                System.arraycopy(bytes, 0, b, offs, len);
            }

            synchronized (accessLock) {
                if (property == null) {
                    try {
                        this.property = property = database.executeTransaction(s -> {
                            Entity entity = s.find(Entity.class, getEntityId());

                            com.github.manevolent.jbot.database.model.Property newProperty =
                                    new com.github.manevolent.jbot.database.model.Property(
                                            database,
                                            entity,
                                            name
                                    );

                            s.persist(newProperty);

                            return newProperty;
                        });
                    } catch (SQLException e) {
                        throw new RuntimeException(e);
                    }
                }

                try {
                    property.setValue(b);
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            }

            return len;
        }

        @Override
        public int read(byte[] bytes, int offs, int len) {
            byte[] b = getValue();
            if (b == null) return 0;
            System.arraycopy(b, 0, bytes, offs, len);
            return len;
        }
    }
}
