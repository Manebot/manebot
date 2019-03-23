package com.github.manevolent.jbot.plugin;

import com.github.manevolent.jbot.Bot;
import com.github.manevolent.jbot.artifact.ArtifactIdentifier;
import com.github.manevolent.jbot.security.Permission;

import java.util.Collection;
import java.util.concurrent.Callable;

public final class DefaultPluginRegistration implements PluginRegistration {
    private final Bot bot;
    private final com.github.manevolent.jbot.database.model.Plugin plugin;
    private final PluginManager pluginManager;
    private final Callable<Plugin> loader;
    private final ArtifactIdentifier identifier;

    private final Object loadLock = new Object();
    private Plugin instance;

    public DefaultPluginRegistration(Bot bot,
                                     com.github.manevolent.jbot.database.model.Plugin plugin,
                                     PluginManager pluginManager,
                                     ArtifactIdentifier identifier,
                                     Callable<Plugin> loader) {
        this.bot = bot;
        this.plugin = plugin;
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
    public void setAutoStart(boolean b) throws SecurityException {
        Permission.checkPermission("system.plugin.autostart.change");

        plugin.setEnabled(b);
    }

    @Override
    public boolean willAutoStart() {
        return plugin.isEnabled();
    }

    @Override
    public Collection<PluginProperty> getProperties() {
        return plugin.getProperties();
    }

    @Override
    public String getProperty(String name) {
        PluginProperty property = plugin.getProperty(name);
        if (property == null) return null;
        else return property.getValue();
    }

    @Override
    public void setProperty(String name, String value) throws SecurityException {
        Permission.checkPermission("system.plugin.property.change");
        plugin.setProperty(name, value);
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
