package com.github.manevolent.jbot.plugin.java;

import com.github.manevolent.jbot.artifact.*;
import com.github.manevolent.jbot.command.CommandManager;
import com.github.manevolent.jbot.database.DatabaseManager;
import com.github.manevolent.jbot.event.EventManager;
import com.github.manevolent.jbot.platform.PlatformManager;
import com.github.manevolent.jbot.plugin.Plugin;
import com.github.manevolent.jbot.plugin.PluginException;
import com.github.manevolent.jbot.plugin.PluginLoadException;

import com.github.manevolent.jbot.plugin.PluginManager;
import com.github.manevolent.jbot.plugin.java.classloader.ClassSource;
import com.github.manevolent.jbot.plugin.java.classloader.LocalClassLoader;
import com.github.manevolent.jbot.plugin.java.classloader.LocalURLClassLoader;
import com.github.manevolent.jbot.plugin.loader.PluginLoader;

import java.io.FileNotFoundException;

import java.io.IOException;
import java.io.InputStream;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public final class JavaPluginLoader implements PluginLoader {
    private final PluginManager pluginManager;
    private final DatabaseManager databaseManager;
    private final CommandManager commandManager;
    private final PlatformManager platformManager;
    private final EventManager eventManager;
    private final Map<ArtifactIdentifier, JavaPluginInstance> pluginInstances = new LinkedHashMap<>();

    public JavaPluginLoader(PluginManager pluginManager,
                            DatabaseManager databaseManager,
                            CommandManager commandManager,
                            PlatformManager platformManager,
                            EventManager eventManager) {
        this.pluginManager = pluginManager;
        this.databaseManager = databaseManager;
        this.commandManager = commandManager;
        this.platformManager = platformManager;
        this.eventManager = eventManager;
    }

    @Override
    public Plugin load(LocalArtifact artifact) throws PluginLoadException, FileNotFoundException {
        if (pluginInstances.containsKey(artifact.getIdentifier()))
            return pluginInstances.get(artifact.getIdentifier()).getPlugin();

        // Transform the JAR/CLASS file into a URL for a URLClassLoader and instantiate pluginClassLoader.
        final URL classpathUrl;
        try {
            classpathUrl = artifact.getFile().toURI().toURL();
        } catch (MalformedURLException e) {
            throw new PluginLoadException(e);
        }
        final LocalURLClassLoader pluginClassLoader = new LocalURLClassLoader(new URL[] { classpathUrl }, null);

        // Collect runtime/classpath dependencies, obtain() all of them, then install them into a classLoader
        Collection<ArtifactDependency> dependencyDefinitions = artifact.getDependencies();
        List<URL> dependencies = new LinkedList<>();
        for (ArtifactDependency dependency : dependencyDefinitions) {
            switch (dependency.getType()) {
                case TEST:
                    continue;
                case COMPILE:
                case RUN:
                    // associate URL of dependency with the dependency list
                    try {
                        dependencies.add(dependency.getChild().obtain().getFile().toURI().toURL());
                    } catch (ArtifactRepositoryException e) {
                        throw new PluginLoadException("failed to load library artifact " + dependency.toString(), e);
                    } catch (MalformedURLException e) {
                        throw new PluginLoadException(e);
                    }
                case PROVIDED:
                    // "shared" libraries
                    // PROVIDED dependencies are treated as shared dependencies, run in the classpath of the dependency
                    // graph, and are attached typically as plugins.  jbot-core is usually ignored, though the API
                    // version the plugin references in its pom.xml is important, and will be validated.
                    throw new UnsupportedOperationException(); //TODO
            }
        }
        URL[] libraryURLArray = new URL[dependencies.size()];
        dependencies.toArray(libraryURLArray);
        final LocalClassLoader libraryClassLoader = new LocalURLClassLoader(libraryURLArray, null);

        // Read properties
        Properties properties;
        try (InputStream inputStream = pluginClassLoader.getLocalResourceAsStream("/jbot-plugin.properties")) {
            if (inputStream == null)
                throw new PluginLoadException("plugin properties not found: jbot-plugin.properties");

            properties = new Properties();
            properties.load(inputStream);
        } catch (IOException e) {
            throw new PluginLoadException(e);
        }

        // Make sure that the plugin artifact is the correct artifact (just a consistency check)
        if (!properties.containsKey("java.plugin.artifact"))
            throw new PluginLoadException(
                    "plugin properties missing \"artifact\" definition, should be: "
                    + artifact.getIdentifier().toString() + " (version definition optional)"
            );
        String artifactSelfName = properties.get("artifact").toString();
        ArtifactIdentifier artifactIdentifier = ArtifactIdentifier.fromString(artifactSelfName);
        if (!artifactIdentifier.getPackageId().equals(artifact.getIdentifier().getPackageId()) ||
                !artifactIdentifier.getArtifactId().equals(artifact.getIdentifier().getArtifactId()) ||
                (
                        artifactIdentifier.getVersion() != null &&
                        !artifactIdentifier.getVersion().equals(artifact.getIdentifier().getVersion())
                ))
            throw new PluginLoadException(
                    "jbot-plugin.properties \"artifact\" property mismatch: was " +
                            artifactIdentifier + ", expected " + artifact.getIdentifier()
            );

        // Collect all JavaPlugin dependencies and obtain() those, too.  We treat these dependencies as Java plugins.
        String[] pluginDependencyArtifacts = properties.getOrDefault("java.plugin.depends", "").toString().split("\\,");

        ArtifactRepository repository = artifact.getManifest().getRepository();

        for (String dependencyArtifactIdentifier : pluginDependencyArtifacts) {
            ArtifactIdentifier identifier = ArtifactIdentifier.fromString(dependencyArtifactIdentifier);
            LocalArtifact pluginDependencyArtifact;

            try {
                pluginDependencyArtifact = repository.getArtifact(identifier).obtain();
            } catch (ArtifactRepositoryException e) {
                throw new PluginLoadException("failed to find plugin dependency artifact " + identifier.toString(), e);
            }


            try {
                load(pluginDependencyArtifact);
            } catch (PluginLoadException e) {
                throw new PluginLoadException("failed to load plugin dependency " + identifier.toString(), e);
            }
        }

        String className = properties.getProperty("java.plugin.class");

        // Define a future task for plugin loading.  This lambda is executed by JavaPluginInstance to load a plugin.
        Loader loader = classLoader -> {
            // Load plugin on a separate thread so that setContextClassLoader() works appropriately.
            CompletableFuture<PluginEntry> loaderFuture = new CompletableFuture<>();

            new Thread(() -> {
                try {
                    Class<? extends PluginEntry> pluginEntryClass;

                    Thread.currentThread().setContextClassLoader(pluginClassLoader);

                    try {
                        Class<?> clazz = pluginClassLoader.loadClass(className);
                        if (clazz == null) throw new NullPointerException();

                        if (!PluginEntry.class.isAssignableFrom(clazz))
                            throw new PluginLoadException(
                                    "class " + clazz.getName() + " not assignable from " + Plugin.class.getName()
                            );

                        // Cast (ignore that unsuppressed warning, we already check that previously/at runtime)
                        pluginEntryClass = (Class<? extends PluginEntry>) clazz;
                    } catch (ClassNotFoundException e) {
                        throw new PluginLoadException(e);
                    }


                    PluginEntry pluginEntry;

                    try {
                        pluginEntry = pluginEntryClass.newInstance();
                    } catch (ReflectiveOperationException e) {
                        throw new PluginLoadException("instantiating new instance of " + pluginEntryClass.getName(), e);
                    }

                    loaderFuture.complete(pluginEntry);
                } catch (Throwable e) {
                    loaderFuture.completeExceptionally(e);
                }
            }).start();

            // wait for execution to complete (join).
            final PluginEntry pluginEntry;

            try {
                pluginEntry = loaderFuture.get();
            } catch (InterruptedException e) {
                throw new PluginLoadException("interrupted while waiting for plugin load to complete", e);
            } catch (ExecutionException e) {
                throw new PluginLoadException(e);
            }

            Plugin.Builder builder = new JavaPlugin.Builder(
                    platformManager, commandManager, pluginManager, databaseManager, eventManager,
                    artifact,
                    null // TODO
            );

            try {
                return pluginEntry.instantiate(builder);
            } catch (PluginException e) {
                throw new PluginLoadException(e);
            }
        };

        JavaPluginInstance instance = new JavaPluginInstance(
                artifact,
                pluginClassLoader, ClassSource.URL_CLASS_SOURCE,
                libraryClassLoader,
                loader,
                null
        );

        Plugin plugin = instance.load();

        pluginInstances.put(artifact.getIdentifier(), instance);

        return plugin;
    }

    public interface Loader {
        Plugin load(ClassLoader classLoader) throws PluginLoadException;
    }
}
