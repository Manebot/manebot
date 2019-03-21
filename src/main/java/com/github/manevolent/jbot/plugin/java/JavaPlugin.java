package com.github.manevolent.jbot.plugin.java;

import com.github.manevolent.jbot.artifact.Artifact;
import com.github.manevolent.jbot.artifact.ManifestIdentifier;
import com.github.manevolent.jbot.command.CommandManager;
import com.github.manevolent.jbot.command.executor.CommandExecutor;
import com.github.manevolent.jbot.database.Database;
import com.github.manevolent.jbot.database.DatabaseManager;
import com.github.manevolent.jbot.event.EventListener;
import com.github.manevolent.jbot.event.EventManager;
import com.github.manevolent.jbot.platform.Platform;
import com.github.manevolent.jbot.platform.PlatformManager;
import com.github.manevolent.jbot.platform.PlatformRegistration;
import com.github.manevolent.jbot.plugin.*;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class JavaPlugin implements Plugin, EventListener {
    private final PlatformManager platformManager;
    private final CommandManager commandManager;
    private final PluginManager pluginManager;
    private final EventManager eventManager;
    private final Artifact artifact;

    private final Map<ManifestIdentifier, Plugin> dependencyMap;
    private final Collection<Function<Platform.Builder, PlatformRegistration>> platformBuilders;
    private final Map<String, CommandExecutor> commandExecutors;
    private final Collection<EventListener> eventListeners;
    private final Collection<PluginFunction> enable;
    private final Collection<PluginFunction> disable;
    private final Map<Class<? extends PluginReference>,
            Function<PluginRegistration, ? extends PluginReference>> instanceMap;
    private final Collection<Database> databases;

    private final Map<Class<? extends PluginReference>, PluginReference> instances = new LinkedHashMap<>();
    private final Collection<PlatformRegistration> platforms = new LinkedList<>();
    private final Collection<CommandManager.Registration> registeredCommands = new LinkedList<>();

    private final Object enableLock = new Object();
    private boolean enabled = false;

    private JavaPlugin(PlatformManager platformManager,
                       CommandManager commandManager,
                       PluginManager pluginManager,
                       EventManager eventManager,
                       Artifact artifact,
                       Map<ManifestIdentifier, Plugin> dependencyMap,
                       Collection<Function<Platform.Builder, PlatformRegistration>> platformBuilders,
                       Map<String, CommandExecutor> commandExecutors,
                       Collection<EventListener> eventListeners,
                       Collection<PluginFunction> enable,
                       Collection<PluginFunction> disable,
                       Map<Class<? extends PluginReference>,
                               Function<PluginRegistration, ? extends PluginReference>> instanceMap,
                       Collection<Database> databases) {
        this.platformManager = platformManager;
        this.commandManager = commandManager;
        this.pluginManager = pluginManager;
        this.eventManager = eventManager;
        this.artifact = artifact;
        this.dependencyMap = dependencyMap;
        this.platformBuilders = platformBuilders;
        this.commandExecutors = commandExecutors;
        this.eventListeners = eventListeners;
        this.enable = enable;
        this.disable = disable;
        this.instanceMap = instanceMap;
        this.databases = databases;
    }

    @Override
    public final Collection<Platform> getPlatforms() {
        return Collections.unmodifiableCollection(
                platforms.stream()
                .map(PlatformRegistration::getPlatform)
                        .collect(Collectors.toList())
        );
    }

    @Override
    public Collection<Database> getDatabases() {
        return Collections.unmodifiableCollection(databases);
    }

    @Override
    public final Collection<String> getCommands() {
        return Collections.unmodifiableCollection(commandExecutors.keySet());
    }

    @Override
    public final String getName() {
        return artifact.getManifest().getArtifactId().toLowerCase();
    }

    @Override
    public <T extends PluginReference> T getInstance(Class<? extends T> aClass) {
        if (!isEnabled()) throw new IllegalStateException("Plugin not enabled");
        Object instance =  instances.get(aClass);
        if (instance == null) return (T) null;
        else return (T) instance;
    }

    @Override
    public PluginRegistration getRegistration() {
        return pluginManager.getPlugin(getArtifact().getIdentifier());
    }

    @Override
    public final Artifact getArtifact() {
        return artifact;
    }

    @Override
    public Collection<Plugin> getDependencies() {
        return Collections.unmodifiableCollection(dependencyMap.values());
    }

    @Override
    public final boolean setEnabled(boolean enabled) throws PluginException {
        synchronized (enableLock) {
            if (this.enabled != enabled) {
                if (enabled) {
                    onEnable();
                    this.enabled = true;
                    onEnabled();
                } else {
                    onDisable();
                    this.enabled = false;
                    onDisabled();
                }
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    private void onEnable() throws PluginException {
        // Register platforms
        for (Function<Platform.Builder, PlatformRegistration> function : platformBuilders) {
            Platform.Builder builder = platformManager.buildPlatform();
            platforms.add(function.apply(builder));
        }

        // Register all commands
        for (String command : commandExecutors.keySet())
            registeredCommands.add(commandManager.registerExecutor(command, commandExecutors.get(command)));

        // Register event listeners
        for (EventListener listener : eventListeners)
            eventManager.registerListener(listener);

        // Register instances
        for (Class<? extends PluginReference> instanceClass : instanceMap.keySet()) {
            Function<PluginRegistration, ? extends PluginReference> instantiator = instanceMap.get(instanceClass);
            PluginReference reference = instantiator.apply(getRegistration());
            reference.load(JavaPlugin.this);
            instances.put(instanceClass, reference);
        }

        // Call all enables
        for (PluginFunction function : enable)
            function.call();
    }

    private void onEnabled() throws PluginException { }

    private void onDisable() throws PluginException {
        // Unregister commands
        Iterator<PlatformRegistration> commandIterator = platforms.iterator();
        while (commandIterator.hasNext()) {
            commandManager.unregisterExecutor(commandIterator.next().getName());
            commandIterator.remove();
        }

        // Unregister event listeners
        for (EventListener listener : eventListeners)
            eventManager.unregisterListener(listener);

        // Unregister instances
        Iterator<Map.Entry<Class<? extends PluginReference>, PluginReference>> instanceIterator =
                instances.entrySet().iterator();
        while (instanceIterator.hasNext()) {
            Map.Entry<Class<? extends PluginReference>, PluginReference> instance = instanceIterator.next();
            instance.getValue().unload(this);
            instanceIterator.remove();
        }

        // Unregister platforms
        Iterator<PlatformRegistration> platformIterator = platforms.iterator();
        while (platformIterator.hasNext()) {
            PlatformRegistration platform = platformIterator.next();

            if (platform.getConnection() != null)
                platform.getConnection().disconnect();

            platformManager.unregisterPlatform(platform);

            platformIterator.remove();
        }
    }

    private void onDisabled() throws PluginException {
        // Call all disables
        for (PluginFunction function : disable)
            function.call();
    }

    public static class Builder implements Plugin.Builder {
        private final PlatformManager platformManager;
        private final CommandManager commandManager;
        private final PluginManager pluginManager;
        private final DatabaseManager databaseManager;
        private final EventManager eventManager;
        private final Artifact artifact;
        private final Map<ManifestIdentifier, Plugin> dependencyMap;

        private final Collection<Function<Platform.Builder, PlatformRegistration>> platformBuilders = new LinkedList<>();
        private final Collection<EventListener> eventListeners = new LinkedList<>();
        private final Collection<PluginFunction> enable = new LinkedList<>();
        private final Collection<PluginFunction> disable = new LinkedList<>();
        private final Map<String, CommandExecutor> commandExecutors = new LinkedHashMap<>();
        private final Map<Class<? extends PluginReference>, Function<PluginRegistration, ? extends PluginReference>>
                instanceMap = new LinkedHashMap<>();
        private final Collection<Database> databases = new LinkedList<>();

        public Builder(PlatformManager platformManager,
                       CommandManager commandManager,
                       PluginManager pluginManager,
                       DatabaseManager databaseManager,
                       EventManager eventManager,
                       Artifact artifact,
                       Map<ManifestIdentifier, Plugin> dependencyMap) {
            this.platformManager = platformManager;
            this.commandManager = commandManager;
            this.pluginManager = pluginManager;
            this.databaseManager = databaseManager;
            this.eventManager = eventManager;
            this.artifact = artifact;
            this.dependencyMap = dependencyMap;
        }

        @Override
        public PluginManager getPluginManager() {
            return pluginManager;
        }

        @Override
        public Artifact getArtifact() {
            return artifact;
        }

        @Override
        public Plugin getDependency(ManifestIdentifier manifestIdentifier) {
            return dependencyMap.get(manifestIdentifier);
        }

        @Override
        public Plugin.Builder command(String label, CommandExecutor executor) {
            commandExecutors.put(label, executor);
            return this;
        }

        @Override
        public Plugin.Builder listen(EventListener eventListener) {
            eventListeners.add(eventListener);
            return this;
        }

        @Override
        public Plugin.Builder platform(Function<Platform.Builder, PlatformRegistration> function) {
            platformBuilders.add(function);
            return this;
        }

        @Override
        public <T extends PluginReference>
        Plugin.Builder instance(Class<T> aClass, Function<PluginRegistration, T> function) {
            instanceMap.put(aClass, function);
            return this;
        }

        @Override
        public Plugin.Builder onEnable(PluginFunction pluginFunction) {
            enable.add(pluginFunction);
            return this;
        }

        @Override
        public Plugin.Builder onDisable(PluginFunction pluginFunction) {
            disable.add(pluginFunction);
            return this;
        }

        @Override
        public Database database(String s, Function<Database.ModelConstructor, Database> function) {
            Database database = databaseManager.defineDatabase(
                    getArtifact().getIdentifier().withoutVersion().toString() + "_" + s,
                    function
            );

            databases.add(database);

            return database;
        }

        @Override
        public Plugin build() {
            return new JavaPlugin(
                    platformManager,
                    commandManager,
                    pluginManager,
                    eventManager,
                    getArtifact(),
                    dependencyMap,
                    platformBuilders,
                    commandExecutors,
                    eventListeners,
                    enable,
                    disable,
                    instanceMap,
                    databases
            );
        }
    }
}
