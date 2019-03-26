package com.github.manevolent.jbot.plugin.java;

import com.github.manevolent.jbot.Version;
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
import com.github.manevolent.jbot.virtual.Virtual;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class JavaPluginLoader implements PluginLoader {
    private static final ManifestIdentifier coreDependency =
            new ManifestIdentifier("com.github.manevolent", "jbot-core");

    private final PluginManager pluginManager;
    private final DatabaseManager databaseManager;
    private final CommandManager commandManager;
    private final PlatformManager platformManager;
    private final EventManager eventManager;
    private final Map<ManifestIdentifier, JavaPluginInstance> pluginInstances = new LinkedHashMap<>();

    private final DefaultArtifactVersion apiVersion;

    public JavaPluginLoader(PluginManager pluginManager,
                            DatabaseManager databaseManager,
                            CommandManager commandManager,
                            PlatformManager platformManager,
                            EventManager eventManager,
                            DefaultArtifactVersion apiVersion) {
        this.pluginManager = pluginManager;
        this.databaseManager = databaseManager;
        this.commandManager = commandManager;
        this.platformManager = platformManager;
        this.eventManager = eventManager;
        this.apiVersion = apiVersion;
    }

    private Collection<LocalArtifact> obtainDependencyTree(ArtifactDependency dependency,
                                                           Set<String> mavenIdentifiers)
            throws ArtifactRepositoryException {
        List<LocalArtifact> artifacts = new LinkedList<>();

        if (mavenIdentifiers.contains(dependency.getChild().toString())) {
            return artifacts;
        }

        if (!dependency.getChild().hasObtained())
            Virtual.getInstance().currentProcess().getLogger().info(
                    "Downloading dependency " + dependency.getChild().toString()
                            + " for " + dependency.getChild().toString() + "..."
            );

        try {
            artifacts.add(dependency.getChild().obtain());
        } catch (ArtifactRepositoryException ex) {
            if (dependency.isRequired()) throw ex;
            else {
                Virtual.getInstance().currentProcess().getLogger().log(
                        Level.WARNING,
                        "Failed to download optional dependency " + dependency.getChild().toString()
                                + " for " + dependency.getChild().toString() + "...",
                        ex
                );

                return Collections.emptyList();
            }
        }

        for (ArtifactDependency child : dependency.getChild().getDependencies()) {
            switch (dependency.getType()) {
                case COMPILE:
                case RUN:
                    Collection<LocalArtifact> obtainedDependencies = obtainDependencyTree(child, mavenIdentifiers);
                    artifacts.addAll(obtainedDependencies);
                    mavenIdentifiers.addAll(
                            obtainedDependencies.stream()
                            .map(LocalArtifact::toString)
                            .collect(Collectors.toList())
                    );
                    break;
            }
        }

        return artifacts;
    }

    @Override
    public Collection<ArtifactDependency> getDependencies(Artifact artifact) {
        Collection<ArtifactDependency> dependencyDefinitions = artifact.getDependencies();
        Collection<ArtifactDependency> pluginDependencies = new LinkedList<>();

        for (ArtifactDependency dependency : dependencyDefinitions) {
            ArtifactIdentifier dependencyIdentifier = dependency.getChild().getIdentifier();
            switch (dependency.getType()) {
                case PROVIDED:
                    ManifestIdentifier dependencyManifestIdentifier = dependencyIdentifier.withoutVersion();
                    if (coreDependency.equals(dependencyManifestIdentifier))
                        continue;
                    pluginDependencies.add(dependency);
                    break;
            }
        }

        return pluginDependencies;
    }

    private JavaPluginInstance loadIntl(LocalArtifact artifact) throws PluginLoadException {
        if (pluginInstances.containsKey(artifact.getIdentifier().withoutVersion())) {
            JavaPluginInstance instance = pluginInstances.get(artifact.getIdentifier().withoutVersion());

            // Check to see if the classloader has booted this Plugin instance.  If it has, there is nothing we can do.
            // We cannot unloaded classes that have already been loaded into the JRE.
            if (instance.isLoaded()) {
                // This plugin is loaded, check the version we are trying to load.
                Version attempted;
                try {
                    attempted = Version.fromString(artifact.getVersion());
                } catch (IllegalArgumentException ex) {
                    // You're on your own here.
                    return instance;
                }

                Version loaded;
                try {
                    loaded = Version.fromString(instance.getArtifact().getVersion());
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

        // All dependencies, which we'll need to calculate associations from
        Collection<ArtifactDependency> dependencyDefinitions = artifact.getDependencies();

        // Simple JARfile dependencies.
        Set<URL> dependencies = new HashSet<>();

        // Provided (plugin) dependencies; more complex version coalescing system than above dependencies
        final Map<ManifestIdentifier, JavaPluginDependency> providedDependencies
                = new LinkedHashMap<>();

        // Collect runtime/classpath dependencies, obtain() all of them, then install them into a classLoader
        // Sorts deps into three categories:
        // 1.  Ones it doesn't care about
        // 2.  Provided dependencies, which are either,
        //     a.  Ignored, in the care of jbot-core, which most/all Plugins would require.
        //     b.  Installed as a Plugin otherwise (recursion on this method) - providedDependencies map
        // 3.  Traditional compile-time and run-time dependencies which are simply loaded into a classloader with the
        //     Plugin being loaded in this context. - dependencies set
        for (ArtifactDependency dependency : dependencyDefinitions) {
            Artifact dependencyArtifact = dependency.getChild();
            ArtifactIdentifier dependencyIdentifier = dependency.getChild().getIdentifier();

            switch (dependency.getType()) {
                case SYSTEM:
                    if (dependency.isRequired())
                        throw new PluginLoadException(
                                "Cannot depend on required system dependency: " + dependency.toString()
                        );
                    //(drop through to continue below)
                case TEST:
                    continue;
                case COMPILE:
                case RUN:
                    // associate URL of dependency with the dependency list
                    try {
                        ManifestIdentifier manifestIdentifier = dependencyIdentifier.withoutVersion();
                        if (coreDependency.equals(manifestIdentifier))
                            throw new PluginLoadException(
                                    "Cannot overload core API dependency which is already provided in " +
                                            "the system classpath: " +
                                            dependencyIdentifier +
                                            " (this dependency be marked as \"provided\")"
                            );

                        // Download dependency and sub-dependencies
                        Collection<LocalArtifact> dependencyTree = obtainDependencyTree(dependency, new HashSet<>());
                        for (LocalArtifact dependentArtifact : dependencyTree)
                            dependencies.add(dependentArtifact.getFile().toURI().toURL());
                    } catch (ArtifactRepositoryException | MalformedURLException e) {
                        throw new PluginLoadException("Failed to load dependency artifact " + dependency.toString(), e);
                    }

                    break;
                case PROVIDED:
                    // First step is to anonymize the dependency relationship
                    ManifestIdentifier dependencyManifestIdentifier = dependencyIdentifier.withoutVersion();

                    // "shared" libraries
                    // PROVIDED dependencies are treated as shared dependencies, run in the classpath of the dependency
                    // graph, and are attached typically as plugins.  jbot-core is usually ignored, though the API
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
                                    "Downloading dependency " + dependencyArtifact.getIdentifier()
                                            + " for " + artifact.getIdentifier() + "..."
                            );

                        try {
                            providedDependency = loadIntl(dependencyArtifact.obtain());
                        } catch (ArtifactRepositoryException e) {
                            PluginLoadException exception = new PluginLoadException(
                                    "Failed to load dependency artifact " + dependency.toString() + " for " +
                                    " plugin " + artifact.getIdentifier(),
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
        try (InputStream inputStream = pluginClassLoader.getLocalResourceAsStream("jbot-plugin.properties")) {
            if (inputStream == null)
                throw new PluginLoadException("Plugin properties not found: jbot-plugin.properties");

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
                            "Problem loading non-optional plugin dependency " +
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

                dependencyInstance.getInstance().addDepender(dependencyInstance);
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

            JavaPlugin.Builder builder = new JavaPlugin.Builder(
                    javaPluginInstance,
                    platformManager,
                    commandManager,
                    pluginManager,
                    databaseManager,
                    eventManager,
                    artifact,
                    pluginDependencies
            );

            try {
                JavaPlugin plugin = (JavaPlugin) pluginEntry.instantiate(builder);

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
        return loadIntl(artifact).load();
    }

    public interface Loader {
        JavaPlugin load(JavaPluginInstance javaPluginInstance, ClassLoader classLoader) throws PluginLoadException;
    }
}