package com.github.manevolent.jbot.plugin;

import com.github.manevolent.jbot.JBot;
import com.github.manevolent.jbot.artifact.*;
import com.github.manevolent.jbot.command.CommandManager;
import com.github.manevolent.jbot.database.DatabaseManager;
import com.github.manevolent.jbot.event.EventExecutionException;
import com.github.manevolent.jbot.event.EventManager;
import com.github.manevolent.jbot.event.plugin.PluginRegisteredEvent;
import com.github.manevolent.jbot.platform.PlatformManager;
import com.github.manevolent.jbot.plugin.java.JavaPluginLoader;
import com.github.manevolent.jbot.plugin.loader.PluginLoaderRegistry;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.io.FileNotFoundException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

public final class DefaultPluginManager implements PluginManager {
    private static final Class<com.github.manevolent.jbot.database.model.Plugin> pluginClass =
            com.github.manevolent.jbot.database.model.Plugin.class;

    private final PluginLoaderRegistry pluginLoaderRegistry;
    private final JBot bot;

    private final Set<Plugin> plugins = new HashSet<>();
    private final Map<ManifestIdentifier, PluginRegistration> pluginMap = new LinkedHashMap<>();

    private final Object installLock = new Object();

    public DefaultPluginManager(JBot bot,
                                EventManager eventManager,
                                DatabaseManager databaseManager,
                                CommandManager commandManager,
                                PlatformManager platformManager
    ) {
        this.bot = bot;
        this.pluginLoaderRegistry = new PluginLoaderRegistry();
        this.pluginLoaderRegistry.registerLoader("jar",
                new JavaPluginLoader(
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
        return pluginMap.get(id);
    }

    private PluginRegistration getOrLoadRegistration(com.github.manevolent.jbot.database.model.Plugin plugin) {
        PluginRegistration registration = plugin.getRegistration();

        if (registration == null) {
            plugin.setRegistration(registration = new DefaultPluginRegistration(
                    bot,
                    this,
                    plugin.getArtifactIdentifier(),
                    () -> load(plugin.getArtifactIdentifier())
            ));

            pluginMap.put(plugin.getArtifactIdentifier().withoutVersion(), registration);
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
            throw new IllegalStateException(localArtifact.getIdentifier().withoutVersion() + " is already loaded.");

        Plugin plugin = getLoaderRegistry().getLoader(localArtifact.getFile()).load(localArtifact);

        plugins.add(plugin);

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
    public PluginRegistration install(ArtifactIdentifier artifactIdentifier)
            throws IllegalArgumentException, PluginLoadException {
        com.github.manevolent.jbot.database.model.Plugin plugin;

        synchronized (installLock) {
             plugin = bot.getSystemDatabase().execute(s -> {
                return s.createQuery(
                        "SELECT x FROM " + pluginClass.getName() + " x " +
                                "WHERE x.packageId = :packageId and x.artifactId = :artifactId",
                        pluginClass
                )
                        .setParameter("packageId", artifactIdentifier.getPackageId())
                        .setParameter("artifactId", artifactIdentifier.getArtifactId())
                        .getResultList()
                        .stream()
                        .findFirst()
                        .orElse(null);
            });

            if (plugin == null) {
                final PluginRegistration registration =
                        new DefaultPluginRegistration(
                                bot,
                                this,
                                artifactIdentifier,
                                () -> load(artifactIdentifier)
                        );

                Plugin instance = registration.load();
                if (instance == null) throw new NullPointerException();

                try {
                    plugin = bot.getSystemDatabase().executeTransaction(s -> {

                        com.github.manevolent.jbot.database.model.Plugin newPlugin =
                                new com.github.manevolent.jbot.database.model.Plugin(
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
        throw new UnsupportedOperationException();
    }

    @Override
    public ArtifactIdentifier resolveIdentifier(String identifier) {
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

                if (existingIdentifier != null) return existingIdentifier;
            } catch (Exception ex2) {
                // Do nothing
            }

            try {
                ManifestIdentifier manifestIdentifier = ManifestIdentifier.fromString(identifier);
                return getRepostiory().getManifest(manifestIdentifier).getLatestVersion();
            } catch (ArtifactRepositoryException ex3) {
                throw new IllegalArgumentException("Problem resolving identifier", ex3);
            }
        }
    }
}
