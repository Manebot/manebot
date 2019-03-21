package com.github.manevolent.jbot.plugin;

import com.github.manevolent.jbot.Bot;
import com.github.manevolent.jbot.artifact.ArtifactIdentifier;

import java.util.concurrent.Callable;

public class DefaultPluginRegistration implements PluginRegistration {
    private final Bot bot;
    private final PluginManager pluginManager;
    private final Callable<Plugin> loader;
    private final ArtifactIdentifier identifier;

    private final Object loadLock = new Object();
    private Plugin instance;

    public DefaultPluginRegistration(Bot bot,
                                     PluginManager pluginManager,
                                     ArtifactIdentifier identifier,
                                     Callable<Plugin> loader) {
        this.bot = bot;
        this.pluginManager = pluginManager;
        this.loader = loader;
        this.identifier = identifier;
    }

    @Override
    public Bot getBot() {
        return bot;
    }

    @Override
    public ArtifactIdentifier getIdentifier() {
        return identifier;
    }

    @Override
    public PluginManager getPluginManager() {
        return pluginManager;
    }

    @Override
    public Plugin getInstance() {
        return instance;
    }

    @Override
    public Plugin load() throws PluginLoadException {
        synchronized (loadLock) {
            if (isLoaded()) throw new IllegalStateException("Plugin already loaded");

            try {
                return instance = loader.call();
            } catch (PluginLoadException e) {
                throw e;
            } catch (Exception e) {
                throw new PluginLoadException(e);
            }
        }
    }
}
