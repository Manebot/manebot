package com.github.manevolent.jbot.plugin.java;

import com.github.manevolent.jbot.artifact.LocalArtifact;
import com.github.manevolent.jbot.plugin.Plugin;
import com.github.manevolent.jbot.plugin.PluginLoadException;
import com.github.manevolent.jbot.plugin.java.classloader.ClassSource;
import com.github.manevolent.jbot.plugin.java.classloader.JavaPluginClassLoader;
import com.github.manevolent.jbot.plugin.java.classloader.LocalClassLoader;

import java.util.Collection;

public final class JavaPluginInstance {
    private final LocalArtifact artifact;
    private final JavaPluginClassLoader classLoader;
    private final JavaPluginLoader.Loader pluginLoader;
    private final Collection<JavaPluginInstance> dependencyInstances;

    private final Object loadLock = new Object();
    private Plugin instance;

    JavaPluginInstance(LocalArtifact artifact,
                       LocalClassLoader pluginClassLoader, ClassSource source,
                       LocalClassLoader libraryClassLoader,
                       JavaPluginLoader.Loader pluginLoader,
                       Collection<JavaPluginInstance> dependencyInstances) {
        this.artifact = artifact;

        this.classLoader = new JavaPluginClassLoader(
                this,
                pluginClassLoader,
                source,
                libraryClassLoader
        );

        this.pluginLoader = pluginLoader;

        this.dependencyInstances = dependencyInstances;
    }

    public Collection<JavaPluginInstance> getDependencies() {
        return dependencyInstances;
    }

    public JavaPluginClassLoader getClassLoader() {
        return classLoader;
    }

    public boolean isLoaded() {
        return instance != null;
    }

    public Plugin load() throws PluginLoadException {
        synchronized (loadLock) {
            if (!isLoaded())
                this.instance = pluginLoader.load(classLoader);
        }

        return this.instance;
    }

    public Plugin getPlugin() {
        return instance;
    }

    public LocalArtifact getArtifact() {
        return artifact;
    }
}
