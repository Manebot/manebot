package io.manebot.plugin;

import io.manebot.DefaultBot;
import io.manebot.artifact.*;
import io.manebot.command.CommandManager;
import io.manebot.database.DatabaseManager;
import io.manebot.database.model.PluginProperty;
import io.manebot.event.EventExecutionException;
import io.manebot.event.EventManager;
import io.manebot.event.plugin.PluginRegisteredEvent;
import io.manebot.platform.PlatformManager;
import io.manebot.plugin.java.JavaPluginLoader;
import io.manebot.plugin.loader.PluginLoader;
import io.manebot.plugin.loader.PluginLoaderRegistry;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public final class DefaultPluginManager implements PluginManager {
    private static final Properties defaultPlugins = new Properties();
    static {
        try {
            defaultPlugins.load(
                    new InputStreamReader(JavaPluginLoader.class.getResourceAsStream("/default-plugins.properties"))
            );
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final Class<io.manebot.database.model.Plugin> pluginClass =
            io.manebot.database.model.Plugin.class;

    private final PluginLoaderRegistry pluginLoaderRegistry;
    private final DefaultBot bot;

    private final Set<Plugin> plugins = new HashSet<>();
    private final Map<ManifestIdentifier, PluginRegistration> pluginMap = new LinkedHashMap<>();
    private final Map<PluginRegistration, io.manebot.database.model.Plugin> modelMap
            = new LinkedHashMap<>();

    private final Object installLock = new Object();

    public DefaultPluginManager(DefaultBot bot,
                                EventManager eventManager,
                                DatabaseManager databaseManager,
                                CommandManager commandManager,
                                PlatformManager platformManager
    ) {
        this.bot = bot;
        this.pluginLoaderRegistry = new PluginLoaderRegistry();
        this.pluginLoaderRegistry.registerLoader("jar",
                new JavaPluginLoader(
                        bot,
                        this,
                        databaseManager,
                        commandManager,
                        platformManager,
                        eventManager,
                        bot.getApiVersion() == null ? null : new DefaultArtifactVersion(bot.getApiVersion().toString())
                )
        );
    }

    @Override
    public ArtifactRepository getRepostiory() {
        return bot.getRepository();
    }

    @Override
    public PluginLoaderRegistry getLoaderRegistry() {
        return pluginLoaderRegistry;
    }

    @Override
    public PluginRegistration getPlugin(ManifestIdentifier id) {
        return getOrLoadRegistration(bot.getSystemDatabase().execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + pluginClass.getName() +" x " +
                            "WHERE x.packageId = :packageId and x.artifactId = :artifactId",
                    pluginClass
            )
                    .setParameter("packageId", id.getPackageId())
                    .setParameter("artifactId", id.getArtifactId())
                    .getResultList()
                    .stream()
                    .findFirst()
                    .orElse(null);
        }));
    }

    private PluginRegistration getOrLoadRegistration(io.manebot.database.model.Plugin plugin) {
        if (plugin == null) return null;

        PluginRegistration registration = plugin.getRegistration();

        if (registration == null) {
            registration = new DefaultPluginRegistration(
                    bot,
                    plugin, this,
                    plugin.getArtifactIdentifier(),
                    () -> load(plugin.getArtifactIdentifier())
            );

            plugin.setRegistration(registration);

            pluginMap.put(plugin.getArtifactIdentifier().withoutVersion(), registration);
            modelMap.put(registration, plugin);
        }

        return registration;
    }

    private Plugin load(ArtifactIdentifier artifactIdentifier)
            throws ArtifactRepositoryException, FileNotFoundException, PluginLoadException {
        return load(getRepostiory().getArtifact(artifactIdentifier).obtain());
    }

    private Plugin load(LocalArtifact localArtifact)
            throws PluginLoadException, FileNotFoundException {
        PluginRegistration registration = getPlugin(localArtifact.getIdentifier().withoutVersion());
        if (registration != null && registration.isLoaded())
            throw new IllegalStateException(
                    localArtifact.getIdentifier().withoutVersion() +
                    " is already registered and loaded."
            );

        Plugin plugin = getLoaderRegistry().getLoader(localArtifact.getFile()).load(localArtifact);

        synchronized (installLock) {
            plugins.add(plugin);
        }

        try {
            bot.getEventDispatcher().execute(new PluginRegisteredEvent(this, plugin));
        } catch (EventExecutionException e) {
            throw new PluginLoadException(e);
        }

        return plugin;
    }

    @Override
    public Collection<Plugin> getLoadedPlugins() {
        return Collections.unmodifiableCollection(plugins);
    }

    @Override
    public Collection<PluginRegistration> getPlugins() {
        return Collections.unmodifiableCollection(bot.getSystemDatabase().execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + pluginClass.getName() +" x ",
                    pluginClass
            ).getResultList()
                    .stream()
                    .map(this::getOrLoadRegistration)
                    .collect(Collectors.toList());
        }));
    }

    @Override
    public Collection<ArtifactDependency> getDependencies(ArtifactIdentifier artifactIdentifier)
            throws ArtifactRepositoryException {
        Artifact artifact = getRepostiory().getArtifact(artifactIdentifier);
        PluginLoader loader = getLoaderRegistry().getLoader(artifact.getExtension());
        return loader.getDependencies(artifact);
    }

    @Override
    public PluginRegistration install(ArtifactIdentifier artifactIdentifier)
            throws IllegalArgumentException, PluginLoadException {
        if (isInstalled(artifactIdentifier))
            throw new IllegalArgumentException("Plugin is already installed: " + artifactIdentifier);

        io.manebot.database.model.Plugin plugin;

        synchronized (installLock) {
             plugin = bot.getSystemDatabase().execute(s -> {
                return s.createQuery(
                        "SELECT x FROM " + pluginClass.getName() + " x " +
                                "WHERE x.packageId = :packageId and x.artifactId = :artifactId",
                        pluginClass
                )
                        .setParameter("packageId", artifactIdentifier.getPackageId())
                        .setParameter("artifactId", artifactIdentifier.getArtifactId())
                        .setMaxResults(1)
                        .getResultList()
                        .stream()
                        .findFirst()
                        .orElse(null);
            });

            if (plugin == null) {
                final PluginRegistration registration =
                        new DefaultPluginRegistration(
                                bot,
                                null,
                                this,
                                artifactIdentifier,
                                () -> load(artifactIdentifier)
                        );

                Plugin instance = registration.load();
                if (instance == null) throw new NullPointerException();

                try {
                    plugin = bot.getSystemDatabase().executeTransaction(s -> {

                        io.manebot.database.model.Plugin newPlugin =
                                new io.manebot.database.model.Plugin(
                                        bot.getSystemDatabase(),
                                        artifactIdentifier
                                );

                        s.persist(newPlugin);

                        newPlugin.setRegistration(registration);

                        return newPlugin;
                    });
                } catch (SQLException e) {
                    throw new RuntimeException(e);
                }
            } else {
               /* if (!plugin.getArtifactIdentifier().getVersion().equals(artifactIdentifier.getVersion()))
                    throw new IllegalStateException(
                            "Another version of " + artifactIdentifier.toString()
                                    + " is already installed: " + plugin.getArtifactIdentifier().getVersion());
                else
                    throw new IllegalStateException(
                            "Plugin already installed: " + plugin.getArtifactIdentifier()
                    );
                */
            }
        }

        return getOrLoadRegistration(plugin);
    }

    @Override
    public boolean uninstall(PluginRegistration pluginRegistration) {
        io.manebot.database.model.Plugin plugin = modelMap.get(pluginRegistration);
        if (plugin == null) return false;

        synchronized (installLock) {
            Collection<PluginProperty> properties = plugin.getProperties();

            try {
                bot.getSystemDatabase().executeTransaction(s -> {
                    for (PluginProperty property : properties) s.remove(property);
                    s.remove(plugin);
                });

                if (pluginRegistration.isLoaded()) plugins.remove(pluginRegistration.getInstance());
                modelMap.remove(pluginRegistration);
                pluginMap.remove(pluginRegistration.getIdentifier().withoutVersion());
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

        return true;
    }

    @Override
    public boolean isInstalled(ArtifactIdentifier artifactIdentifier) {
        return bot.getSystemDatabase().execute(s -> {
            return s.createQuery(
                    "SELECT x FROM " + pluginClass.getName() + " x " +
                            "WHERE x.packageId = :packageId and x.artifactId = :artifactId and x.version = :version",
                    pluginClass
            )
                    .setParameter("packageId", artifactIdentifier.getPackageId())
                    .setParameter("artifactId", artifactIdentifier.getArtifactId())
                    .setParameter("version", artifactIdentifier.getVersion())
                    .setMaxResults(1)
                    .getResultList()
                    .stream()
                    .findAny();
        }).isPresent();
    }

    private ArtifactIdentifier resolveIdentifier(ManifestIdentifier manifestIdentifier) {
        ArtifactIdentifier existingIdentifier =
                getLoadedPlugins()
                        .stream()
                        .filter(p -> p.getArtifact().getIdentifier().withoutVersion().equals(manifestIdentifier))
                        .map(x -> x.getArtifact().getIdentifier())
                        .findFirst()
                        .orElse(null);

        if (existingIdentifier != null)
            return existingIdentifier;
        else
            try {
                return getRepostiory().getManifest(manifestIdentifier).getLatestVersion();
            } catch (ArtifactRepositoryException ex3) {
                throw new IllegalArgumentException("Problem resolving identifier", ex3);
            }
    }

    @Override
    public ArtifactIdentifier resolveIdentifier(String identifier) {
        if (defaultPlugins.containsKey(identifier.toLowerCase()))
            return resolveIdentifier(ManifestIdentifier.fromString(defaultPlugins.getProperty(identifier)));

        try {
            return ArtifactIdentifier.fromString(identifier);
        } catch (IllegalArgumentException ex) {
            try {
                ArtifactIdentifier existingIdentifier =
                        getLoadedPlugins()
                                .stream()
                                .filter(p ->
                                        p.getArtifact().getIdentifier().getArtifactId()
                                        .equalsIgnoreCase(identifier)
                                ).map(x -> x.getArtifact().getIdentifier())
                                .findFirst()
                                .orElse(null);

                if (existingIdentifier != null)
                    return existingIdentifier;
            } catch (Exception ex2) {
                // Do nothing
            }

            return resolveIdentifier(ManifestIdentifier.fromString(identifier));
        }
    }
}
