package io.manebot.plugin;

import io.manebot.Bot;
import io.manebot.artifact.ArtifactIdentifier;
import io.manebot.security.Permission;
import io.manebot.user.UserType;
import io.manebot.virtual.Virtual;

import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

public final class DefaultPluginRegistration implements PluginRegistration {
    private final Bot bot;
    private final io.manebot.database.model.Plugin plugin;
    private final PluginManager pluginManager;
    private final Callable<Plugin> loader;
    private final ArtifactIdentifier identifier;

    private final Object loadLock = new Object();
    private Plugin instance;

    public DefaultPluginRegistration(Bot bot,
                                     io.manebot.database.model.Plugin plugin,
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
    public void setRequired(boolean required) throws SecurityException {
        Permission.checkPermission("system.plugin.required.change");

        plugin.setRequired(required);
    }

    @Override
    public boolean isRequired() {
        return plugin.isRequired();
    }

    @Override
    public void setElevated(boolean elevated) throws SecurityException {
        Permission.checkPermission("system.plugin.elevated.change");
        if (Virtual.getInstance().currentUser().getType() != UserType.SYSTEM)
            throw new SecurityException("Cannot change property as non-system user.");

        plugin.setElevated(true);
    }

    @Override
    public boolean isElevated() {
        return plugin.isElevated();
    }

    @Override
    public Collection<PluginProperty> getProperties() {
        return plugin.getProperties().stream().map(x -> (PluginProperty) x).collect(Collectors.toList());
    }

    @Override
    public void setVersion(String version) {
        plugin.setVersion(version);
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
            if (isLoaded()) return instance;

            try {
                instance = loader.call();
                return instance;
            } catch (PluginLoadException e) {
                throw e;
            } catch (Exception e) {
                throw new PluginLoadException(e);
            }
        }
    }
}
