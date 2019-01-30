package com.github.manevolent.jbot.plugin.java;

import com.github.manevolent.jbot.artifact.*;
import com.github.manevolent.jbot.plugin.Plugin;
import com.github.manevolent.jbot.plugin.PluginLoadException;

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
    private Map<ArtifactIdentifier, JavaPluginInstance> pluginInstances = new LinkedHashMap<>();

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
        GlobalArtifactRepository libraryRepository = new GlobalArtifactRepository(artifact.getRepositories());
        Collection<ArtifactDependency> dependencyDefinitions = artifact.getDependencies();
        List<URL> dependencies = new LinkedList<>();
        for (ArtifactDependency dependency : dependencyDefinitions) {
            if (dependency.getType() == ArtifactDependencyLevel.COMPILE) continue;

            Artifact manifest;

            // pull artifact from repository
            try {
                 manifest = libraryRepository.getArtifact(dependency);
            } catch (ArtifactRepositoryException e) {
                throw new PluginLoadException("failed to find library artifact " + dependency.toString(), e);
            }

            // associate URL of dependency with the dependency list
            try {
                dependencies.add(manifest.obtain().getFile().toURI().toURL());
            } catch (ArtifactRepositoryException e) {
                throw new PluginLoadException("failed to load library artifact " + dependency.toString(), e);
            } catch (MalformedURLException e) {
                throw new PluginLoadException(e);
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
            CompletableFuture<Plugin> loaderFuture = new CompletableFuture<>();

            new Thread(() -> {
                try {
                    Class<? extends Plugin> pluginClass;

                    Thread.currentThread().setContextClassLoader(pluginClassLoader);

                    try {
                        Class<?> clazz = pluginClassLoader.loadClass(className);
                        if (clazz == null) throw new NullPointerException();

                        if (!Plugin.class.isAssignableFrom(clazz))
                            throw new PluginLoadException(
                                    "class " + clazz.getName() + " not assignable from " + Plugin.class.getName()
                            );

                        // Cast (ignore that unsuppressed warning, we already check that previously/at runtime)
                        pluginClass = (Class<? extends Plugin>) clazz;
                    } catch (ClassNotFoundException e) {
                        throw new PluginLoadException(e);
                    }


                    Plugin plugin;

                    try {
                        plugin = pluginClass.getConstructor(LocalArtifact.class).newInstance(artifact);
                    } catch (ReflectiveOperationException e) {
                        throw new PluginLoadException("instantiating new instance of " + pluginClass.getName(), e);
                    }

                    loaderFuture.complete(plugin);
                } catch (Throwable e) {
                    loaderFuture.completeExceptionally(e);
                }
            }).start();

            // wait for execution to complete (join).
            final Plugin plugin;

            try {
                plugin = loaderFuture.get();
            } catch (InterruptedException e) {
                throw new PluginLoadException("interrupted while waiting for plugin load to complete", e);
            } catch (ExecutionException e) {
                throw new PluginLoadException(e);
            }

            return plugin;
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
