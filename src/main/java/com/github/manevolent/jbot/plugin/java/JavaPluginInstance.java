package com.github.manevolent.jbot.plugin.java;

import com.github.manevolent.jbot.artifact.LocalArtifact;
import com.github.manevolent.jbot.artifact.ManifestIdentifier;
import com.github.manevolent.jbot.plugin.Plugin;
import com.github.manevolent.jbot.plugin.PluginLoadException;
import com.github.manevolent.jbot.plugin.java.classloader.ClassSource;
import com.github.manevolent.jbot.plugin.java.classloader.JavaPluginClassLoader;
import com.github.manevolent.jbot.plugin.java.classloader.LocalClassLoader;

import java.util.*;

public final class JavaPluginInstance {
    private final LocalArtifact artifact;
    private final JavaPluginClassLoader classLoader;
    private final JavaPluginLoader.Loader pluginLoader;
    private final Map<ManifestIdentifier, JavaPluginDependency> dependencies;
    private final Collection<JavaPluginDependency> dependers = new HashSet<>();

    private final Object loadLock = new Object();
    private JavaPlugin instance;

    JavaPluginInstance(LocalArtifact artifact,
                       LocalClassLoader pluginClassLoader,
                       ClassSource source,
                       LocalClassLoader libraryClassLoader,
                       JavaPluginLoader.Loader pluginLoader,
                       Map<ManifestIdentifier, JavaPluginDependency> dependencies) {
        this.artifact = artifact;

        this.classLoader = new JavaPluginClassLoader(
                this,
                pluginClassLoader,
                source,
                libraryClassLoader
        );

        this.pluginLoader = pluginLoader;
        this.dependencies = dependencies;
    }

    public JavaPluginDependency getDependency(ManifestIdentifier manifestIdentifier) {
        return dependencies.get(manifestIdentifier);
    }

    public void addDepender(JavaPluginDependency dependency) {
        dependers.add(dependency);
    }

    public void removeDepender(JavaPluginDependency dependency) {
        dependers.remove(dependency);
    }

    public Collection<JavaPluginDependency> getDependers() {
        return Collections.unmodifiableCollection(dependers);
    }

    public Collection<JavaPluginDependency> getDependencies() {
        return dependencies.values();
    }

    public JavaPluginClassLoader getClassLoader() {
        return classLoader;
    }

    public boolean isLoaded() {
        return instance != null;
    }

    public JavaPlugin load() throws PluginLoadException {
        synchronized (loadLock) {
            if (!isLoaded())
                this.instance = pluginLoader.load(this, classLoader);
        }

        return this.instance;
    }

    public JavaPlugin getPlugin() {
        return instance;
    }

    public LocalArtifact getArtifact() {
        return artifact;
    }
}
