package io.manebot.plugin.java;

import io.manebot.Bot;
import io.manebot.artifact.*;
import io.manebot.command.CommandManager;
import io.manebot.database.DatabaseManager;
import io.manebot.event.EventManager;
import io.manebot.platform.PlatformManager;
import io.manebot.plugin.Plugin;
import io.manebot.plugin.PluginException;
import io.manebot.plugin.PluginLoadException;
import io.manebot.plugin.PluginManager;
import io.manebot.plugin.java.classloader.ClassSource;
import io.manebot.plugin.java.classloader.LocalClassLoader;
import io.manebot.plugin.java.classloader.LocalURLClassLoader;
import io.manebot.plugin.loader.PluginLoader;
import io.manebot.security.ElevationDispatcher;
import io.manebot.virtual.Virtual;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class JavaPluginLoader implements PluginLoader {
    private static final ManifestIdentifier coreDependency =
            new ManifestIdentifier("io.manebot", "manebot-core");

    private final Bot bot;

    private final PluginManager pluginManager;
    private final DatabaseManager databaseManager;
    private final CommandManager commandManager;
    private final PlatformManager platformManager;
    private final EventManager eventManager;
    private final Map<ManifestIdentifier, JavaPluginInstance> pluginInstances = new LinkedHashMap<>();

    private final DefaultArtifactVersion apiVersion;
    private final Function<ManifestIdentifier, ElevationDispatcher> elevationDispatcherFunction;

    public JavaPluginLoader(Bot bot,
                            PluginManager pluginManager,
                            DatabaseManager databaseManager,
                            CommandManager commandManager,
                            PlatformManager platformManager,
                            EventManager eventManager,
                            DefaultArtifactVersion apiVersion,
                            Function<ManifestIdentifier, ElevationDispatcher> elevationDispatcherFunction) {
        this.bot = bot;

        this.pluginManager = pluginManager;
        this.databaseManager = databaseManager;
        this.commandManager = commandManager;
        this.platformManager = platformManager;
        this.eventManager = eventManager;
        this.apiVersion = apiVersion;
        this.elevationDispatcherFunction = elevationDispatcherFunction;
    }

    /**
     * Gets plugin dependencies for a given artifact.
     * @param artifact artifact.
     * @return collection of artifact dependencies.
     */
    @Override
    public Collection<ArtifactDependency> getDependencies(Artifact artifact) throws ArtifactNotFoundException {
        Collection<ArtifactDependency> dependencyDefinitions =
                artifact.getDependencies().stream()
                        .filter(dependency -> dependency.getType() == ArtifactDependencyLevel.PROVIDED)
                        .collect(Collectors.toList());

        Collection<ArtifactDependency> pluginDependencies = new LinkedList<>();

        for (ArtifactDependency dependency : dependencyDefinitions) {
            ArtifactIdentifier dependencyIdentifier = dependency.getChild().getIdentifier();
            ManifestIdentifier dependencyManifestIdentifier = dependencyIdentifier.withoutVersion();
            if (coreDependency.equals(dependencyManifestIdentifier))
                continue;

            pluginDependencies.add(dependency);
            break;
        }

        return pluginDependencies;
    }

    private JavaPluginInstance loadIntl(LocalArtifact artifact) throws PluginLoadException, ArtifactNotFoundException {
        if (pluginInstances.containsKey(artifact.getIdentifier().withoutVersion())) {
            JavaPluginInstance instance = pluginInstances.get(artifact.getIdentifier().withoutVersion());

            // Check to see if the classloader has booted this Plugin instance.  If it has, there is nothing we can do.
            // We cannot unloaded classes that have already been loaded into the JRE.
            if (instance.isLoaded()) {
                // This plugin is loaded, check the version we are trying to load.
                ArtifactVersion attempted;
                try {
                    attempted = new DefaultArtifactVersion(artifact.getVersion());
                } catch (IllegalArgumentException ex) {
                    // You're on your own here.
                    return instance;
                }

                ArtifactVersion loaded;
                try {
                    loaded = new DefaultArtifactVersion(instance.getArtifact().getVersion());
                } catch (IllegalArgumentException ex) {
                    // You're on your own here.
                    return instance;
                }

                if (attempted.compareTo(loaded) < 0)
                    throw new PluginLoadException(
                            "Attempted to load " + artifact.getIdentifier() + ", but " +
                            artifact.getIdentifier().withoutVersion()
                            + " was already loaded previously with later version " + loaded.toString() + "."
                    );

                if (!attempted.equals(loaded))
                    Logger.getGlobal().warning(
                            "Instead of loading " + artifact.getIdentifier() + ", shadowing with version " +
                                    loaded + " as it is already in the classloader."
                    );

                return instance;
            }
        }

        Logger.getGlobal().info("Loading " + artifact.toString() + "...");

        // Transform the JAR/CLASS file into a URL for a URLClassLoader and instantiate pluginClassLoader.
        final URL classpathUrl;
        try {
            classpathUrl = artifact.getFile().toURI().toURL();
        } catch (MalformedURLException e) {
            throw new PluginLoadException(e);
        }
        final LocalURLClassLoader pluginClassLoader = new LocalURLClassLoader(new URL[] { classpathUrl }, null);

        // Provided (plugin) dependencies
        Collection<ArtifactDependency> providedArtifacts = getDependencies(artifact);
        final Map<ManifestIdentifier, JavaPluginDependency> providedDependencies = new LinkedHashMap<>();
        for (ArtifactDependency dependency : providedArtifacts) {
            ArtifactIdentifier dependencyIdentifier = dependency.getChild().getIdentifier();
            Artifact dependencyArtifact = dependency.getChild();

            // First step is to anonymize the dependency relationship
            ManifestIdentifier dependencyManifestIdentifier = dependencyIdentifier.withoutVersion();

            // "shared" libraries
            // PROVIDED dependencies are treated as shared dependencies, run in the classpath of the dependency
            // graph, and are attached typically as plugins.  manebot-core is usually ignored, though the API
            // version the plugin references in its pom.xml is important, and will be validated.

            // Check to see if it is already provided in this classpath (static assignment)
            if (coreDependency.equals(dependencyManifestIdentifier)) {
                DefaultArtifactVersion requiredApiVersion =
                        new DefaultArtifactVersion(dependencyIdentifier.getVersion());

                if (apiVersion != null &&
                        !requiredApiVersion.equals(apiVersion) &&
                        requiredApiVersion.compareTo(apiVersion) > 0)
                    throw new PluginLoadException(
                            artifact.getIdentifier() +
                                    " requires API version " +
                                    dependencyIdentifier.getVersion().toString()
                                    + ", but API version is " +
                                    apiVersion.toString() +
                                    "."
                    );
                else continue; // Ignore, good version etc.
            }

            JavaPluginInstance providedDependency;

            // Find if the plugin has already been loaded into the system.
            if (pluginInstances.containsKey(dependencyManifestIdentifier)) {
                providedDependency = pluginInstances.get(dependencyManifestIdentifier);
            } else {
                if (!dependencyArtifact.hasObtained())
                    Virtual.getInstance().currentProcess().getLogger().info(
                            "Downloading dependency " + dependencyArtifact.toString()
                                    + " for " + artifact.toString() + "..."
                    );

                try {
                    providedDependency = loadIntl(dependencyArtifact.obtain());
                } catch (Throwable e) {
                    PluginLoadException exception = new PluginLoadException(
                            "Failed to load dependency artifact " +
                                    dependencyArtifact.toString() + " for " +
                                    " plugin " + artifact.toString(),
                            e
                    );

                    if (dependency.isRequired())
                        throw exception;
                    else {
                        Logger.getGlobal().log(
                                Level.WARNING,
                                "Problem loading optional plugin dependency",
                                exception
                        );

                        // skip loading this plugin, as it's marked optional
                        continue;
                    }
                }
            }

            providedDependencies.put(
                    dependencyManifestIdentifier,
                    new JavaPluginDependency(
                            providedDependency, // The JavaPluginInstance we *want* to use, which may not be possible
                            new DefaultArtifactVersion(dependencyIdentifier.getVersion()),
                            dependency
                    ));

            break;
        }

        // Get all compile-time dependencies
        Collection<ArtifactDependency> compileDependencies = artifact.getDependencyGraph();
        Set<URL> dependencies = new HashSet<>();
        for (ArtifactDependency dependency : compileDependencies) {
            Artifact dependencyArtifact = dependency.getChild();

            try {
                if (!dependencyArtifact.hasObtained())
                    Virtual.getInstance().currentProcess().getLogger().info(
                            "Downloading dependency " + dependencyArtifact.toString()
                                    + " for " + artifact.toString() + "..."
                    );

                dependencies.add(dependency.getChild().obtain().getFile().toURL());
            } catch (Throwable e) {
                PluginLoadException exception = new PluginLoadException(
                        "Failed to load dependency artifact " +
                                dependencyArtifact.toString() + " for " +
                                " plugin " + artifact.toString(),
                        e
                );

                if (dependency.isRequired())
                    throw exception;
                else {
                    Logger.getGlobal().log(
                            Level.WARNING,
                            "Problem loading optional plugin dependency",
                            exception
                    );
                }
            }
        }

        // Make sure all "provided" dependencies (now stored in providedDependencies) and the JavaPluginInstance objects
        // we have associated with each anonymized ManifestIdentifier (i.e. "package:artifact") is the appropriate
        // version to be using in coordination with this plugin.  If not, the user needs to update the version of the
        // underlying Plugin that is being loaded in this context.
        Collection<JavaPluginDependency> conflicts = providedDependencies
                .values()
                .stream()
                .filter(dependency -> dependency.getMinimumVersion().compareTo(
                    new DefaultArtifactVersion(dependency.getInstance().getArtifact().getVersion())) > 0)
                .collect(Collectors.toList());
        if (conflicts.size() > 0)
            throw new PluginLoadException(
                    artifact.getIdentifier() +
                            " has shared dependency conflicts; update dependent plugins to resolve: " +
                            String.join(", ",
                                    conflicts.stream().map(conflict ->
                                            conflict.getInstance().getArtifact().getIdentifier().toString()
                                                    + " < required " +
                                                    conflict.getMinimumVersion().toString()).collect(Collectors.toList())
                            )
            );

        // Load runtime dependencies
        URL[] libraryURLArray = new URL[dependencies.size()];
        dependencies.toArray(libraryURLArray);
        final LocalClassLoader libraryClassLoader = new LocalURLClassLoader(libraryURLArray, null);

        // Read properties
        Properties properties;
        try (InputStream inputStream = pluginClassLoader.getLocalResourceAsStream("manebot-plugin.properties")) {
            if (inputStream == null)
                throw new PluginLoadException("Plugin properties not found: manebot-plugin.properties");

            properties = new Properties();
            properties.load(inputStream);
        } catch (IOException e) {
            throw new PluginLoadException(e);
        }

        String className = properties.getProperty("java.plugin.entry");

        // Define a future task for plugin loading.  This lambda is executed by JavaPluginInstance to load a plugin.
        // The purpose of this to solve a chicken-and-egg scenario where we don't have the JavaPluginClassLoader
        // instance yet.  This is executed synchronously with this thread, as a future lambda.  We pass this into the
        // JavaPluginInstance for future execution by that class's load() method, opportunistically.
        final Map<ManifestIdentifier, Plugin> pluginDependencies = new LinkedHashMap<>();
        Loader loader = (javaPluginInstance, classLoader) -> {
            for (JavaPluginDependency dependencyInstance : providedDependencies.values()) {
                Plugin plugin;

                try {
                    plugin = dependencyInstance.getInstance().load();
                } catch (Throwable e) {
                    PluginLoadException exception = new PluginLoadException(
                            "Problem loading plugin dependency " +
                            dependencyInstance.getInstance().getArtifact().getIdentifier() + " for " +
                            artifact.getIdentifier(),
                            e
                    );

                    if (dependencyInstance.isRequired())
                        throw exception;
                    else {
                        Virtual.getInstance().currentProcess().getLogger().log(
                                Level.WARNING,
                                "Problem loading plugin dependency",
                                exception
                        );

                        continue;
                    }
                }

                pluginDependencies.put(
                        dependencyInstance.getInstance().getArtifact().getIdentifier().withoutVersion(),
                        plugin
                );

                dependencyInstance.getInstance().addDepender(new JavaPluginDependency(
                        javaPluginInstance, // swap instance of the plugin out to that
                        dependencyInstance.getMinimumVersion(),
                        dependencyInstance.getArtifactDependency()
                ));
            }

            // Load plugin on a separate thread so that setContextClassLoader() works appropriately.
            CompletableFuture<PluginEntry> loaderFuture = new CompletableFuture<>();

            Virtual.getInstance().create(() -> {
                try {
                    Class<? extends PluginEntry> pluginEntryClass;

                    Thread.currentThread().setContextClassLoader(classLoader);

                    try {
                        Class<?> clazz = classLoader.loadClass(className);
                        if (clazz == null) throw new NullPointerException();

                        if (!PluginEntry.class.isAssignableFrom(clazz))
                            throw new PluginLoadException(
                                    "class " + clazz.getName() + " not assignable from " + PluginEntry.class.getName()
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

            JavaPlugin.Builder builder = new JavaPlugin.Builder(
                    bot,
                    javaPluginInstance,
                    platformManager,
                    commandManager,
                    pluginManager,
                    databaseManager,
                    eventManager,
                    artifact,
                    pluginDependencies,
                    () -> elevationDispatcherFunction.apply(artifact.getIdentifier().withoutVersion())
            );

            try {
                // Instantiate plugin on a separate thread so that setContextClassLoader() works appropriately.
                // Otherwise, we'd break database and other class loading features
                CompletableFuture<JavaPlugin> instantiationFuture = new CompletableFuture<>();
                Virtual.getInstance().create(() -> {
                    try {
                        Thread.currentThread().setContextClassLoader(classLoader);
                        pluginEntry.instantiate(builder);
                        instantiationFuture.complete(builder.build());
                    } catch (Throwable e) {
                        instantiationFuture.completeExceptionally(e);
                    }
                }).start();

                JavaPlugin plugin;

                try {
                    plugin = instantiationFuture.get();
                } catch (InterruptedException e) {
                    throw new PluginException(e);
                } catch (ExecutionException e) {
                    if (e.getCause() instanceof PluginException)
                        throw (PluginException) e.getCause();

                    throw new PluginException(e.getCause());
                }

                for (JavaPluginDependency dependencyInstance : providedDependencies.values()) {
                    dependencyInstance.getInstance().getPlugin()
                            .getDependencyListeners()
                            .forEach((listener) -> listener.accept(plugin));
                }

                return plugin;
            } catch (PluginException e) {
                throw new PluginLoadException(e);
            }
        };

        JavaPluginInstance instance = new JavaPluginInstance(
                artifact,
                pluginClassLoader,
                ClassSource.URL_CLASS_SOURCE,
                libraryClassLoader,
                loader,
                providedDependencies
        );

        pluginInstances.put(artifact.getIdentifier().withoutVersion(), instance);

        Logger.getGlobal().info("Loaded " + artifact.toString() + ".");

        return instance;
    }

    @Override
    public Plugin load(LocalArtifact artifact) throws PluginLoadException, FileNotFoundException {
        try {
            return loadIntl(artifact).load();
        } catch (ArtifactNotFoundException e) {
            throw new PluginLoadException(e);
        }
    }

    public interface Loader {
        JavaPlugin load(JavaPluginInstance javaPluginInstance, ClassLoader classLoader) throws PluginLoadException;
    }
}