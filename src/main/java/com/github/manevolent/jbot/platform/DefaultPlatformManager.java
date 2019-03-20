package com.github.manevolent.jbot.platform;

import com.github.manevolent.jbot.database.Database;
import com.github.manevolent.jbot.plugin.Plugin;

import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public class DefaultPlatformManager implements PlatformManager {
    private final Object platformLock = new Object();
    private final Database systemDatabase;
    private final HashSet<PlatformRegistration> registrations = new HashSet<>();
    private final HashMap<Platform, PlatformRegistration> registrationMap = new LinkedHashMap<>();
    private final HashMap<String, PlatformRegistration> registrationByNameMap = new LinkedHashMap<>();

    public DefaultPlatformManager(Database systemDatabase) {
        this.systemDatabase = systemDatabase;
    }

    @Override
    public Builder buildPlatform() {
        return new Builder() {
            private com.github.manevolent.jbot.database.model.Platform platform;

            public Builder id(String id) {
                this.platform = getOrCreatePlatformById(id);
                super.id(id);
                return this;
            }

            @Override
            public Platform getPlatform() {
                return platform;
            }

            @Override
            public PlatformRegistration register(Plugin plugin) {
                synchronized (platformLock) {
                    // Should never happen.
                    if (platform == null)
                        throw new NullPointerException("platform");

                    if (platform.getRegistration() != null) {
                        throw new IllegalStateException(
                                "Platform is already registered to another Plugin: " +
                                        platform.getRegistration().getPlugin().getArtifact().getIdentifier()
                        );
                    }

                    DefaultPlatformRegistration registration = new DefaultPlatformRegistration(
                            platform,
                            this,
                            plugin
                    );

                    registrations.add(registration);
                    registrationMap.put(platform, registration);
                    registrationByNameMap.put(registration.getName(), registration);

                    platform.setRegistration(registration);

                    return registration;
                }
            }
        };
    }

    @Override
    public void unregisterPlatform(PlatformRegistration platformRegistration) {
        synchronized (platformLock) {
            if (registrations.remove(platformRegistration)) {
                registrationMap.remove(platformRegistration.getPlatform());
                registrationByNameMap.remove(platformRegistration.getName());

                if (platformRegistration.getConnection().isConnected())
                    platformRegistration.getConnection().disconnect();
            }
        }
    }

    @Override
    public Collection<Platform> getPlatforms() {
        return Collections.unmodifiableCollection(systemDatabase.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + com.github.manevolent.jbot.database.model.Platform.class.getName(),
                    com.github.manevolent.jbot.database.model.Platform.class
            ).getResultList()
            .stream()
                    .map(platform -> (Platform) platform)
                    .collect(Collectors.toList());
        }));
    }

    @Override
    public com.github.manevolent.jbot.database.model.Platform getPlatformById(String id) {
        return systemDatabase.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + com.github.manevolent.jbot.database.model.Platform.class.getName() + " x "
                    + "WHERE x.id = :id",
                    com.github.manevolent.jbot.database.model.Platform.class
            )
                    .setParameter("id", id)
                    .getResultList()
                    .stream()
                    .findFirst()
                    .orElse(null);
        });
    }

    private com.github.manevolent.jbot.database.model.Platform getOrCreatePlatformById(String id) {
        com.github.manevolent.jbot.database.model.Platform platform = getPlatformById(id);

        if (platform == null)
            try {
                platform = systemDatabase.executeTransaction(entityManager -> {
                    com.github.manevolent.jbot.database.model.Platform newPlatform =
                            new com.github.manevolent.jbot.database.model.Platform(systemDatabase, id);

                    entityManager.persist(newPlatform);

                    return newPlatform;
                });
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }

        return platform;
    }

    @Override
    public Platform getPlatformByName(String name) {
        PlatformRegistration registration = registrationByNameMap.get(name);
        if (registration == null) return null;

        return registration.getPlatform();
    }

    @Override
    public Collection<Platform> getPlatformsByPlugin(Plugin plugin) {
        return Collections.unmodifiableCollection(
                registrations
                        .stream()
                        .filter(registration -> registration.getPlugin() == plugin)
                        .map(PlatformRegistration::getPlatform)
                        .collect(Collectors.toList())
        );
    }

    private class DefaultPlatformRegistration implements PlatformRegistration {
        private final String name;
        private final PlatformConnection connection;
        private final Platform platform;
        private final Plugin plugin;

        private DefaultPlatformRegistration(Platform platform, Builder builder, Plugin plugin) {
            this.name = builder.getName();
            this.connection = builder.getConnection();
            this.platform = platform;
            this.plugin = plugin;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public PlatformConnection getConnection() {
            return connection;
        }

        @Override
        public Platform getPlatform() {
            return platform;
        }

        @Override
        public Plugin getPlugin() {
            return plugin;
        }
    }
}