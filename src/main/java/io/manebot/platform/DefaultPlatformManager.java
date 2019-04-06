package io.manebot.platform;

import io.manebot.database.Database;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginException;

import java.sql.SQLException;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class DefaultPlatformManager implements PlatformManager {
    private static final Class<io.manebot.database.model.Platform> platformClass =
            io.manebot.database.model.Platform.class;

    private final Object platformLock = new Object();
    private final Database systemDatabase;
    private final HashSet<PlatformRegistration> registrations = new HashSet<>();
    private final HashMap<Platform, PlatformRegistration> registrationMap = new LinkedHashMap<>();
    private final HashMap<String, PlatformRegistration> registrationByNameMap = new LinkedHashMap<>();

    public DefaultPlatformManager(Database systemDatabase) {
        this.systemDatabase = systemDatabase;
    }

    @Override
    public PlatformRegistration registerPlatform(Consumer<Platform.Builder> consumer) {
        Builder builder = new Builder();
        consumer.accept(builder);

        io.manebot.database.model.Platform platform = builder.getPlatform();
        PlatformRegistration registration = builder.build();

        registrations.add(registration);
        registrationMap.put(platform, registration);
        registrationByNameMap.put(registration.getName(), registration);

        platform.setRegistration(registration);

        return registration;
    }

    @Override
    public void unregisterPlatform(PlatformRegistration platformRegistration) {
        synchronized (platformLock) {
            if (registrations.remove(platformRegistration)) {
                registrationMap.remove(platformRegistration.getPlatform());
                registrationByNameMap.remove(platformRegistration.getName());

                // Fully unregister
                if (platformRegistration.getPlatform() instanceof io.manebot.database.model.Platform)
                    ((io.manebot.database.model.Platform)
                            platformRegistration.getPlatform()).setRegistration(null);

                if (platformRegistration.getConnection().isConnected()) {
                    try {
                        platformRegistration.getConnection().disconnect();
                    } catch (PluginException e) {
                        throw new RuntimeException("Problem disconnecting platform "
                                + platformRegistration.getPlatform().getId(), e);
                    }
                }
            }
        }
    }

    @Override
    public Collection<Platform> getPlatforms() {
        return Collections.unmodifiableCollection(systemDatabase.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + platformClass.getName() + " x",
                    platformClass
            ).getResultList()
            .stream()
                    .map(platform -> (Platform) platform)
                    .collect(Collectors.toList());
        }));
    }

    @Override
    public io.manebot.database.model.Platform getPlatformById(String id) {
        return systemDatabase.execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + platformClass.getName() + " x "
                    + "WHERE x.id = :id",
                    platformClass
            )
                    .setParameter("id", id)
                    .getResultList()
                    .stream()
                    .findFirst()
                    .orElse(null);
        });
    }

    private io.manebot.database.model.Platform getOrCreatePlatformById(String id) {
        io.manebot.database.model.Platform platform = getPlatformById(id);

        if (platform == null)
            try {
                platform = systemDatabase.executeTransaction(entityManager -> {
                    io.manebot.database.model.Platform newPlatform =
                            new io.manebot.database.model.Platform(systemDatabase, id);

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

        private DefaultPlatformRegistration(Platform platform, Platform.Builder builder, Plugin plugin) {
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

    public class Builder extends Platform.Builder {
        private io.manebot.database.model.Platform platform;

        @Override
        public Builder setId(String id) {
            this.platform = getOrCreatePlatformById(id);
            super.setId(id);
            return this;
        }

        @Override
        public io.manebot.database.model.Platform getPlatform() {
            return platform;
        }

        private PlatformRegistration build() {
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

                return new DefaultPlatformRegistration(
                        platform,
                        this,
                        getPlugin()
                );
            }
        }
    }
}
